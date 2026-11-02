package com.stemotion.domain.auth.service;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Rehearses a shared native enum without connecting to the configured/shared database. */
class RolesMigrationRehearsalTest {
    @Test void nativeEnumIsSplitWithoutChangingMembershipsOrCredentials() throws Exception {
        try(var pg=EmbeddedPostgres.builder().start();var c=pg.getDatabase("postgres","postgres").getConnection();var sql=c.createStatement()) {
            sql.execute("CREATE TYPE user_role AS ENUM ('ADMIN','WRITER','REVIEWER','PRODUCER')");
            sql.execute("CREATE TABLE users(id uuid PRIMARY KEY,role user_role NOT NULL,password_hash text,auth_version bigint NOT NULL DEFAULT 0)");
            sql.execute("CREATE TABLE workspace_members(id uuid PRIMARY KEY,user_id uuid REFERENCES users(id),role user_role NOT NULL DEFAULT 'WRITER',is_leader boolean NOT NULL)");
            sql.execute("CREATE TABLE google_login_whitelist(id uuid PRIMARY KEY,email varchar(254),status varchar(16))");
            String admin=UUID.randomUUID().toString(),user=UUID.randomUUID().toString(),seat=UUID.randomUUID().toString();
            sql.execute("INSERT INTO users VALUES ('"+admin+"','ADMIN','fixture-admin-hash',7),('"+user+"','REVIEWER','fixture-user-hash',9)");
            sql.execute("INSERT INTO workspace_members VALUES ('"+seat+"','"+user+"','PRODUCER',true)");
            sql.execute(Files.readString(Path.of("deploy/roles-mail-drafts/01_expand_roles_and_notifications.sql")));
            try(var rows=sql.executeQuery("SELECT system_role,password_hash,auth_version FROM users WHERE id='"+user+"'")) {
                rows.next();assertThat(rows.getString(1)).isEqualTo("USER");
                assertThat(rows.getString(2)).isEqualTo("fixture-user-hash");assertThat(rows.getLong(3)).isEqualTo(10);
            }
            try(var rows=sql.executeQuery("SELECT system_role FROM users WHERE id='"+admin+"'")) {rows.next();assertThat(rows.getString(1)).isEqualTo("ADMIN");}
            try(var rows=sql.executeQuery("SELECT role,is_leader,user_id::text FROM workspace_members WHERE id='"+seat+"'")) {
                rows.next();assertThat(rows.getString(1)).isEqualTo("PRODUCER");assertThat(rows.getBoolean(2)).isTrue();assertThat(rows.getString(3)).isEqualTo(user);
            }
            assertThatThrownBy(()->sql.execute("UPDATE workspace_members SET role='ADMIN'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->sql.execute("UPDATE users SET system_role='WRITER'")).isInstanceOf(SQLException.class);
            try(var rows=sql.executeQuery("SELECT count(*) FROM pg_type WHERE typname='user_role'")) {rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
        }
    }
}
