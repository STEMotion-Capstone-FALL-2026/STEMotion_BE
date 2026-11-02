package com.stemotion.domain.notification;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.GoogleWhitelist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;
import java.time.*;
import java.util.*;

/** Durable notification queue. No provider call is made inside a database transaction. */
@Service @EnableScheduling @lombok.extern.slf4j.Slf4j
public class NotificationService {
    public record Summary(String status, LocalDateTime lastAttemptAt, LocalDateTime nextAttemptAt, boolean canResend) {}
    record Job(String id, String whitelistId, long revision, String recipient, int attempts, String claimToken) {}
    private final JdbcTemplate jdbc;
    private final EmailProperties properties;
    private final NotificationSender sender;
    private final TransactionTemplate transaction;
    public NotificationService(JdbcTemplate jdbc, EmailProperties properties, NotificationSender sender, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.properties = properties; this.sender = sender; this.transaction = new TransactionTemplate(manager);
    }
    @org.springframework.transaction.annotation.Transactional
    public void enqueue(GoogleWhitelist whitelist) {
        if (!properties.isEnabled()) return;
        jdbc.update("insert into notification_outbox(id,whitelist_id,grant_revision,recipient,provider,status) values (?,?,?,?,?, 'PENDING') on conflict (whitelist_id,grant_revision,event_type) do nothing",
            UUID.randomUUID().toString(), whitelist.getId(), whitelist.getGrantRevision(), whitelist.getEmail(),properties.getProvider());
    }
    @org.springframework.transaction.annotation.Transactional
    public void cancel(String whitelistId) {
        jdbc.update("update notification_outbox set status='CANCELLED',updated_at=CURRENT_TIMESTAMP where whitelist_id::text=? and status in ('PENDING','RETRY')", whitelistId);
    }
    public Map<String,Summary> summaries(List<String> ids) {
        var result = new HashMap<String,Summary>();
        ids.forEach(id -> result.put(id, new Summary(properties.isEnabled() ? "NONE" : "DISABLED", null, null, properties.isEnabled())));
        if (!properties.isEnabled() || ids.isEmpty()) return result;
        String slots = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbc.query("select distinct on (n.whitelist_id) n.whitelist_id::text as wid,n.status,n.provider,n.attempt_count,n.updated_at,n.next_attempt_at,(n.updated_at < CURRENT_TIMESTAMP - "+properties.getResendCooldownSeconds()+" * interval '1 second') as cooldown_ready,w.status as access_status from notification_outbox n join google_login_whitelist w on w.id=n.whitelist_id and w.grant_revision=n.grant_revision where n.whitelist_id::text in (" + slots + ") order by n.whitelist_id,n.created_at desc", rs -> {
            var updated = rs.getTimestamp("updated_at").toLocalDateTime();
            var state = rs.getString("status");
            boolean terminal = Set.of("ACCEPTED","FAILED","UNKNOWN").contains(state);
            result.put(rs.getString("wid"), new Summary("local".equals(rs.getString("provider")) && "ACCEPTED".equals(state) ? "PREVIEW" : state,
                rs.getInt("attempt_count") == 0 ? null : updated,
                Set.of("PENDING","RETRY").contains(state) ? rs.getTimestamp("next_attempt_at").toLocalDateTime() : null,
                "ACTIVE".equals(rs.getString("access_status")) && terminal && rs.getBoolean("cooldown_ready")));
        }, ids.toArray());
        return result;
    }
    @org.springframework.transaction.annotation.Transactional
    public void resend(GoogleWhitelist whitelist, String actor) {
        if (!properties.isEnabled()) throw ApiException.conflict("Email thông báo chưa được bật.");
        if (!whitelist.isActive()) throw ApiException.conflict("Gmail đã bị thu hồi quyền.");
        var rows = jdbc.queryForList("select id,status,updated_at,resend_count,(resend_day=CURRENT_DATE) as same_day,(updated_at<=CURRENT_TIMESTAMP - "+properties.getResendCooldownSeconds()+" * interval '1 second') as cooldown_ready from notification_outbox where whitelist_id::text=? and grant_revision=? for update",
            whitelist.getId(), whitelist.getGrantRevision());
        if (rows.isEmpty()) {
            enqueue(whitelist);
            String eventId=jdbc.queryForObject("select id from notification_outbox where whitelist_id::text=? and grant_revision=?",String.class,whitelist.getId(),whitelist.getGrantRevision());
            jdbc.update("insert into notification_audit(event_id,actor_id,action) values (?,?,'SEND_EXISTING_GRANT')",eventId,actor);
            return;
        }
        var row = rows.get(0);
        String status = (String) row.get("status");
        if (!Set.of("ACCEPTED","FAILED","UNKNOWN").contains(status)) throw ApiException.conflict("Thông báo đang chờ xử lý.");
        boolean sameDay = Boolean.TRUE.equals(row.get("same_day"));
        int count = sameDay ? ((Number) row.get("resend_count")).intValue() : 0;
        if (!Boolean.TRUE.equals(row.get("cooldown_ready")) || count >= 5)
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "EMAIL_RESEND_LIMIT", "Vui lòng chờ trước khi gửi lại thông báo.");
        jdbc.update("update notification_outbox set status='PENDING',provider=?,attempt_count=0,next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,error_code=null,claim_token=null,lease_until=null,resend_day=CURRENT_DATE,resend_count=? where id=?", properties.getProvider(), count+1, row.get("id"));
        jdbc.update("insert into notification_audit(event_id,actor_id,action) values (?,?,'RESEND')", row.get("id"), actor);
    }
    @Scheduled(fixedDelayString="${app.notification-email.poll-interval-ms:10000}")
    public void dispatch() {
        if (!properties.isEnabled()) return;
        try {
            for (int i=0; i<10; i++) {
                Job job = transaction.execute(tx -> claim());
                if (job == null) break;
                deliver(job);
            }
        } catch (Exception ex) { log.warn("notification_dispatch_failed type={}", ex.getClass().getSimpleName()); }
    }
    private Job claim() {
        // Once a lease expires the outcome may already have reached the provider. Require explicit resend.
        jdbc.update("update notification_outbox set status='UNKNOWN',error_code='LEASE_EXPIRED',updated_at=CURRENT_TIMESTAMP where status='SENDING' and lease_until<CURRENT_TIMESTAMP");
        jdbc.update("update notification_outbox n set status='CANCELLED',updated_at=CURRENT_TIMESTAMP where n.status in ('PENDING','RETRY') and not exists(select 1 from google_login_whitelist w where w.id=n.whitelist_id and w.status='ACTIVE' and w.grant_revision=n.grant_revision)");
        var jobs = jdbc.query("select id,whitelist_id::text,grant_revision,recipient,attempt_count from notification_outbox where provider=? and status in ('PENDING','RETRY') and next_attempt_at<=CURRENT_TIMESTAMP order by created_at limit 1 for update skip locked",
            (rs,n) -> new Job(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getInt(5)+1,UUID.randomUUID().toString()),properties.getProvider());
        if (jobs.isEmpty()) return null;
        var job = jobs.get(0);
        long leaseSeconds = 30L + (properties.getConnectTimeoutMs() + (long) properties.getReadTimeoutMs())/1000;
        jdbc.update("update notification_outbox set status='SENDING',attempt_count=?,claim_token=?,lease_until=CURRENT_TIMESTAMP+(? * interval '1 second'),updated_at=CURRENT_TIMESTAMP where id=?",
            job.attempts(),job.claimToken(),leaseSeconds,job.id());
        return job;
    }
    @Scheduled(cron="0 0 3 * * *",zone="Asia/Ho_Chi_Minh")
    public void removeExpiredRecipientData() {
        if (!properties.isEnabled()) return;
        jdbc.update("delete from notification_outbox where status in ('ACCEPTED','FAILED','CANCELLED','UNKNOWN') and updated_at<CURRENT_TIMESTAMP-interval '30 days'");
    }
    private void deliver(Job job) {
        Boolean active = jdbc.queryForObject("select exists(select 1 from google_login_whitelist where id::text=? and status='ACTIVE' and grant_revision=?)", Boolean.class, job.whitelistId(), job.revision());
        if (!Boolean.TRUE.equals(active)) { finish(job,"CANCELLED",null,"GRANT_REVOKED",0); return; }
        try {
            String message = sender.send(job.id(), job.recipient(), html(job.recipient()));
            finish(job,"ACCEPTED",message,null,0);
        } catch (NotificationSender.Failure failure) {
            String status = failure.uncertain ? "UNKNOWN" : failure.retryable && job.attempts()<properties.getMaxAttempts() ? "RETRY" : "FAILED";
            long delay = Math.max(failure.retryAfterSeconds, Math.min(3600, 30L << Math.min(job.attempts(),6)) + java.util.concurrent.ThreadLocalRandom.current().nextLong(15));
            finish(job,status,null,failure.code,Math.min(delay,86400));
        } catch (Exception ex) { finish(job,"UNKNOWN",null,"UNEXPECTED_SEND_FAILURE",0); }
    }
    private void finish(Job job, String status, String message, String code, long delay) {
        jdbc.update("update notification_outbox set status=?,provider_message_id=?,error_code=?,next_attempt_at=CURRENT_TIMESTAMP+(? * interval '1 second'),accepted_at=case when ?='ACCEPTED' then CURRENT_TIMESTAMP else accepted_at end,lease_until=null,updated_at=CURRENT_TIMESTAMP where id=? and status='SENDING' and claim_token=?",
            status,message,code,delay,status,job.id(),job.claimToken());
        log.info("notification_result eventId={} status={}",job.id(),status);
    }
    String html(String recipient) {
        return "<!doctype html><html lang=\"vi\"><meta charset=\"utf-8\"><body><h1>Bạn đã được cấp quyền đăng nhập STEMotion</h1><p>Gmail <strong>"
            + HtmlUtils.htmlEscape(recipient) + "</strong> đã được quản trị viên cho phép đăng nhập bằng Google. Quyền truy cập có thể thay đổi sau thông báo này.</p><p><a href=\""
            + HtmlUtils.htmlEscape(properties.getPublicUrl()) + "\">Mở STEMotion</a></p><p>Nếu chưa có tài khoản thường, chọn Đăng nhập bằng Google.</p>"
            + "<p>Nếu đã được cấp tên tài khoản và mật khẩu, hãy đăng nhập bằng mật khẩu rồi liên kết Google trong Cài đặt hồ sơ trước để giữ cùng tài khoản và dữ liệu.</p>"
            + "<p>Thông báo này không tự cấp quyền workspace. Quản trị viên sẽ gán workspace và vai trò khi cần.</p></body></html>";
    }
}
