package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.GoogleWhitelist;
import com.stemotion.domain.auth.repository.GoogleWhitelistRepository;
import com.stemotion.domain.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class GoogleWhitelistService {
    private final GoogleWhitelistRepository records;
    private final NotificationService notifications;
    private final com.stemotion.domain.auth.repository.UserRepository users;
    @Transactional
    public GoogleWhitelist add(String rawEmail, String actor) {
        String email = GoogleAuthService.gmail(rawEmail);
        if (records.lockByEmail(email).isPresent()) throw ApiException.conflict("Gmail đã có trong danh sách. Dùng Khôi phục nếu đã thu hồi.");
        var w = new GoogleWhitelist(); w.setEmail(email); w.setCreatedBy(actor); w.setUpdatedBy(actor);
        records.saveAndFlush(w);
        notifications.enqueue(w);
        log.info("auth_whitelist action=ADD adminId={} whitelistId={}", actor, w.getId());
        return w;
    }
    @Transactional
    public GoogleWhitelist update(String id, String status, long version, String actor) {
        users.lockSystemAdministration();
        var w = records.lockById(id).orElseThrow(() -> ApiException.notFound("Whitelist",id));
        if (w.getVersion() != version) throw ApiException.conflict("Danh sách đã được cập nhật. Vui lòng tải lại.");
        if (status.equals(w.getStatus())) return w;
        var owner = w.getIdentity() == null ? null : w.getIdentity().getUser();
        if ("REVOKED".equals(status) && owner != null
                && owner.getSystemRole() == com.stemotion.domain.auth.entity.SystemRole.ADMIN
                && owner.getStatus() == com.stemotion.domain.auth.entity.User.UserStatus.ACTIVE
                && owner.getPasswordHash() == null
                && users.countAdministratorsWithLogin(com.stemotion.domain.auth.entity.SystemRole.ADMIN,
                    com.stemotion.domain.auth.entity.User.UserStatus.ACTIVE) <= 1)
            throw ApiException.conflict("Không thể thu hồi phương thức đăng nhập cuối cùng của quản trị viên cuối cùng.");
        w.setStatus(status); w.setUpdatedBy(actor); w.setGrantRevision(w.getGrantRevision()+1);
        w.setRevokedAt(w.isActive() ? null : LocalDateTime.now());
        records.saveAndFlush(w);
        if (w.isActive()) notifications.enqueue(w); else notifications.cancel(w.getId());
        log.info("auth_whitelist action={} adminId={} whitelistId={}", status, actor, w.getId());
        return w;
    }
    @Transactional
    public void resend(String id, long version, String actor) {
        var w = records.lockById(id).orElseThrow(() -> ApiException.notFound("Whitelist",id));
        if (w.getVersion() != version) throw ApiException.conflict("Danh sách đã thay đổi. Vui lòng tải lại.");
        notifications.resend(w,actor);
    }
}
