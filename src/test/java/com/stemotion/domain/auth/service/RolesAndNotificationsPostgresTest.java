package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.dto.AuthDtos.UpdateUserRequest;
import com.stemotion.domain.auth.entity.*;
import com.stemotion.domain.auth.repository.*;
import com.stemotion.domain.notification.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration,classpath:db/auth-v2-e2e",
    "spring.jpa.hibernate.ddl-auto=validate", "app.notification-email.enabled=true",
    "app.notification-email.provider=local", "app.notification-email.poll-interval-ms=3600000"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({UserService.class,GoogleWhitelistService.class,NotificationService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RolesAndNotificationsPostgresTest {
    static EmbeddedPostgres postgres;
    static class FakeSender implements NotificationSender {
        final Set<String> accepted = ConcurrentHashMap.newKeySet();
        volatile Failure failure;
        public String send(String event, String recipient, String html) {
            if (failure!=null) throw failure;
            assertThat(html).contains("liên kết Google", "không tự cấp quyền workspace");
            accepted.add(event);
            return "fixture:"+event;
        }
    }
    @TestConfiguration static class Config {
        @Bean DataSource dataSource() throws Exception {
            postgres=EmbeddedPostgres.builder().start();
            return postgres.getDatabase("postgres","postgres",Map.of("stringtype","unspecified"));
        }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean EmailProperties emailProperties() { var p=new EmailProperties();p.setEnabled(true);return p; }
        @Bean FakeSender sender() { return new FakeSender(); }
    }
    @AfterAll static void close() throws Exception { if(postgres!=null)postgres.close(); }
    @Autowired UserRepository users;
    @Autowired UserService accounts;
    @Autowired GoogleWhitelistRepository whitelist;
    @Autowired GoogleIdentityRepository identities;
    @Autowired GoogleWhitelistService access;
    @Autowired NotificationService notifications;
    @Autowired FakeSender sender;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;

    @BeforeEach void reset() {
        for(String table:List.of("notification_outbox","notification_audit","account_security_audit","google_identities","google_login_whitelist","users"))
            jdbc.update("delete from "+table);
        sender.failure=null;sender.accepted.clear();
    }
    User admin(String name) { return users.saveAndFlush(User.builder().fullName(name).systemRole(SystemRole.ADMIN).passwordHash("fixture-admin-hash").username("admin-"+name.toLowerCase()).build()); }

    @Test void demotionInvalidatesSessionsAndCannotRemoveLastAdmin() {
        var first=admin("First");var last=admin("Last");
        var updated=accounts.update(first.getId(),new UpdateUserRequest(null,SystemRole.USER,null,null),first.getVersion());
        assertThat(updated.systemRole()).isEqualTo(SystemRole.USER);
        assertThat(users.findById(first.getId()).orElseThrow().getAuthVersion()).isEqualTo(1);
        assertThatThrownBy(()->accounts.update(first.getId(),new UpdateUserRequest("Stale",null,null,null),0L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->accounts.update(last.getId(),new UpdateUserRequest(null,SystemRole.USER,null,null),last.getVersion())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->accounts.update(last.getId(),new UpdateUserRequest(null,null,User.UserStatus.INACTIVE,null),last.getVersion())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->accounts.delete(last.getId(),last.getVersion())).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("select count(*) from account_security_audit",Integer.class)).isEqualTo(1);
    }
    @Test void twoAdminsCannotDemoteEachOtherConcurrently() throws Exception {
        var a=admin("A");var b=admin("B");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(var user:List.of(a,b))results.add(pool.submit(()->{
                start.await();try{accounts.update(user.getId(),new UpdateUserRequest(null,SystemRole.USER,null,null),0L);return true;}
                catch(ApiException ex){return false;}
            }));
            start.countDown();
            int succeeded=0;for(var result:results)if(result.get(20,TimeUnit.SECONDS))succeeded++;
            assertThat(succeeded).isEqualTo(1);
            assertThat(users.countBySystemRoleAndStatus(SystemRole.ADMIN,User.UserStatus.ACTIVE)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void whitelistAndOutboxRollbackTogetherAndRevokeCancelsOldGrant() {
        var tx=new TransactionTemplate(manager);
        tx.execute(status->{access.add("rollback@gmail.com","admin");status.setRollbackOnly();return null;});
        assertThat(whitelist.findByEmail("rollback@gmail.com")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox",Integer.class)).isZero();
        var row=access.add("allowed@gmail.com","admin");
        access.update(row.getId(),"ACTIVE",row.getVersion(),"admin");
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox",Integer.class)).isEqualTo(1);
        row=access.update(row.getId(),"REVOKED",row.getVersion(),"admin");
        notifications.dispatch();assertThat(sender.accepted).isEmpty();
        row=access.update(row.getId(),"ACTIVE",row.getVersion(),"admin");
        notifications.dispatch();assertThat(sender.accepted).hasSize(1);
        assertThat(notifications.summaries(List.of(row.getId())).get(row.getId()).status()).isEqualTo("PREVIEW");
    }
    @Test void retryFailureAndUnknownOutcomeNeverRevokeWhitelist() {
        var row=access.add("retry@gmail.com","admin");
        sender.failure=new NotificationSender.Failure("PROVIDER_HTTP_429",true,false,120);
        notifications.dispatch();
        assertThat(notifications.summaries(List.of(row.getId())).get(row.getId()).status()).isEqualTo("RETRY");
        jdbc.update("update notification_outbox set next_attempt_at=CURRENT_TIMESTAMP-interval '1 second'");
        sender.failure=new NotificationSender.Failure("PROVIDER_OUTCOME_UNKNOWN",false,true,0);
        notifications.dispatch();notifications.dispatch();
        assertThat(notifications.summaries(List.of(row.getId())).get(row.getId()).status()).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("select attempt_count from notification_outbox",Integer.class)).isEqualTo(2);
        assertThat(whitelist.findById(row.getId()).orElseThrow().isActive()).isTrue();
    }
    @Test void expiredLeaseRequiresExplicitResendAndResendIsRateLimited() {
        var row=access.add("lease@gmail.com","admin");
        jdbc.update("update notification_outbox set status='SENDING',lease_until=CURRENT_TIMESTAMP-interval '1 minute'");
        notifications.dispatch();assertThat(sender.accepted).isEmpty();
        assertThat(notifications.summaries(List.of(row.getId())).get(row.getId()).status()).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->access.resend(row.getId(),row.getVersion(),"admin")).isInstanceOf(ApiException.class);
        jdbc.update("update notification_outbox set updated_at=CURRENT_TIMESTAMP-interval '2 minutes'");
        access.resend(row.getId(),row.getVersion(),"admin");
        assertThatThrownBy(()->access.resend(row.getId(),row.getVersion(),"admin")).isInstanceOf(ApiException.class);
        notifications.dispatch();assertThat(sender.accepted).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from notification_audit",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentDispatchersAcceptEachEventOnce() throws Exception {
        access.add("dispatch@gmail.com","admin");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(notifications::dispatch);var b=pool.submit(notifications::dispatch);
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            assertThat(sender.accepted).hasSize(1);
            assertThat(jdbc.queryForObject("select attempt_count from notification_outbox",Integer.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void googleOnlyAdminCannotLoseTheLastUsableAdministratorLogin() {
        var row=access.add("last-admin@gmail.com","admin");
        var owner=users.saveAndFlush(User.builder().fullName("Google Admin").systemRole(SystemRole.ADMIN).build());
        var identity=new GoogleIdentity();identity.setUser(owner);identity.setWhitelist(row);
        identity.setEmail(row.getEmail());identity.setSubject("fixture-admin-subject");identity.setLinkedAt(java.time.LocalDateTime.now());
        identities.saveAndFlush(identity);
        assertThatThrownBy(()->access.update(row.getId(),"REVOKED",row.getVersion(),"admin"))
            .isInstanceOf(ApiException.class).hasMessageContaining("phương thức đăng nhập cuối cùng");
        var backup=admin("Backup");
        access.update(row.getId(),"REVOKED",row.getVersion(),"admin");
        // An ACTIVE Admin whose Google access was revoked must not count as a usable fallback.
        assertThatThrownBy(()->accounts.update(backup.getId(),new UpdateUserRequest(null,SystemRole.USER,null,null),backup.getVersion()))
            .isInstanceOf(ApiException.class);
    }
}
