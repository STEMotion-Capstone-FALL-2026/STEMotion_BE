package com.stemotion.domain.auth.service;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Runs the actual deployment drafts against throwaway PostgreSQL, never Spring's configured DB. */
class AuthMigrationRehearsalTest {
    @Test void approvedLegacyEmailMappingSeparatesCaseDuplicatesWithoutChangingOwners() throws Exception {
        try(var pg=EmbeddedPostgres.builder().start()) {
            var ds=pg.getDatabase("postgres","postgres",Map.of("stringtype","unspecified"));
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            try(var c=ds.getConnection();var sql=c.createStatement()) {
                String admin=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();
                sql.execute("insert into users(id,full_name,display_name,email,password_hash,role,status,created_at,updated_at) values "
                    +"('"+admin+"','Admin','Admin','Retained@Test.invalid','admin-hash','ADMIN','ACTIVE',now(),now()),"
                    +"('"+other+"','Other','Other','retained@test.invalid','other-hash','WRITER','ACTIVE',now()-interval '1 day',now())");
                sql.execute(Files.readString(Path.of("deploy/auth-v2-drafts/01_expand.sql")));
                sql.execute(Files.readString(Path.of("deploy/auth-v2-drafts/02_retain_legacy_email_usernames.sql")));
                try(var r=sql.executeQuery("select username,password_hash from users where id='"+admin+"'")) {
                    r.next();assertThat(r.getString(1)).isEqualTo("retained@test.invalid");assertThat(r.getString(2)).isEqualTo("admin-hash");
                }
                try(var r=sql.executeQuery("select username,password_hash from users where id='"+other+"'")) {
                    r.next();assertThat(r.getString(1)).isEqualTo("legacy-"+other.replace("-", "").substring(0,24));assertThat(r.getString(2)).isEqualTo("other-hash");
                }
            }
        }
    }
    @Test void reviewedBackfillPreservesUsersAndRejectsMissingMappings() throws Exception {
        try (var pg=EmbeddedPostgres.builder().start()) {
            var ds=pg.getDatabase("postgres","postgres",Map.of("stringtype","unspecified"));
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            try(var connection=ds.getConnection();var sql=connection.createStatement()) {
                String id=UUID.randomUUID().toString();
                sql.execute("insert into users(id,full_name,display_name,email,password_hash,role,status,created_at,updated_at,must_change_password) values ('"
                    +id+"','Migration Admin','Migration Admin','migration-admin@example.invalid','fixture-password-hash','ADMIN','ACTIVE',now(),now(),true)");
                Map<String,String> original=new LinkedHashMap<>();
                try(var rows=sql.executeQuery("select id,password_hash from users where password_hash is not null order by id")) {
                    while(rows.next())original.put(rows.getString(1),rows.getString(2));
                }
                // Match the deployed legacy schema, which requires password_hash.
                sql.execute("alter table users alter column password_hash set not null");
                sql.execute(Files.readString(Path.of("deploy/auth-v2-drafts/01_expand.sql")));
                String backfill=Files.readString(Path.of("deploy/auth-v2-drafts/02_backfill_and_validate.sql"));
                String missingMapping=backfill;
                assertThatThrownBy(()->sql.execute(missingMapping)).isInstanceOf(SQLException.class);
                sql.execute("ROLLBACK");
                try(var rows=sql.executeQuery("select count(*) from users where username is not null")) {rows.next();assertThat(rows.getInt(1)).isZero();}
                StringBuilder mapping=new StringBuilder();int n=0;
                for(String userId:original.keySet())mapping.append("INSERT INTO auth_username_mapping VALUES ('")
                    .append(userId).append("','reviewed_").append(n++).append("');\n");
                backfill=backfill.replace("-- INSERT INTO auth_username_mapping VALUES ('<existing-user-id>', '<reviewed-username>');",mapping.toString());
                sql.execute(backfill);
                try(var rows=sql.executeQuery("select id,password_hash,username from users where password_hash is not null order by id")) {
                    int count=0;while(rows.next()) {assertThat(rows.getString(2)).isEqualTo(original.get(rows.getString(1)));assertThat(rows.getString(3)).startsWith("reviewed_");count++;}
                    assertThat(count).isEqualTo(original.size());
                }
                assertThatThrownBy(()->sql.execute("insert into users(id,full_name,display_name,password_hash,role,status,created_at,updated_at) values ('"
                    +UUID.randomUUID()+"','Invalid User','Invalid User','fixture','WRITER','ACTIVE',now(),now())")).isInstanceOf(SQLException.class);
                String whitelistId=UUID.randomUUID().toString(),identityId=UUID.randomUUID().toString();
                sql.execute("update users set google_id='approved-legacy-sub' where id='"+id+"'");
                String legacy=Files.readString(Path.of("deploy/auth-v2-drafts/03_import_approved_legacy_google.sql"));
                legacy=legacy.replace("-- INSERT INTO approved_legacy_google VALUES",
                    "INSERT INTO approved_legacy_google VALUES ('"+id+"','legacy@gmail.com','approved-legacy-sub','"+whitelistId+"','"+identityId+"','"+id+"');\n-- INSERT INTO approved_legacy_google VALUES");
                sql.execute(legacy);
                try(var rows=sql.executeQuery("select user_id from google_identities where id='"+identityId+"'")) {assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo(id);}
                String googleOnly=UUID.randomUUID().toString();
                sql.execute("insert into users(id,full_name,display_name,role,status,created_at,updated_at) values ('"
                    +googleOnly+"','Google Only','Google Only','WRITER','ACTIVE',now(),now())");
                try(var rows=sql.executeQuery("select password_hash,username from users where id='"+googleOnly+"'")) {
                    assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isNull();assertThat(rows.getString(2)).isNull();
                }
            }
        }
    }
}
