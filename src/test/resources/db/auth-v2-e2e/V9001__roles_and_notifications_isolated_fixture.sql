-- LOCAL DRAFT. Apply after auth-v2 expansion, with old application writers stopped.
-- Never put this draft into production Flyway locations without reviewing the live schema.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM users WHERE role IS NULL OR role::text NOT IN ('ADMIN','WRITER','REVIEWER','PRODUCER'))
 THEN RAISE EXCEPTION 'Unexpected legacy system role; review mapping before continuing'; END IF;
 IF EXISTS (SELECT 1 FROM workspace_members WHERE role IS NULL OR role::text NOT IN ('WRITER','REVIEWER','PRODUCER'))
 THEN RAISE EXCEPTION 'Unexpected workspace role; explicit membership mapping required'; END IF;
END $$;
ALTER TABLE users ADD COLUMN system_role varchar(16);
UPDATE users SET system_role=CASE WHEN role::text='ADMIN' THEN 'ADMIN' ELSE 'USER' END;
ALTER TABLE users ALTER COLUMN system_role SET NOT NULL;
ALTER TABLE users ALTER COLUMN system_role SET DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT users_system_role_check CHECK (system_role IN ('ADMIN','USER'));
ALTER TABLE users ADD COLUMN version bigint NOT NULL DEFAULT 0;
-- Retain legacy values for review/rollback; new application does not write this column.
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;
ALTER TABLE users ALTER COLUMN role DROP DEFAULT;
ALTER TABLE workspace_members ALTER COLUMN role DROP DEFAULT;
ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_role_check;
ALTER TABLE workspace_members ALTER COLUMN role TYPE varchar(20) USING role::text;
ALTER TABLE workspace_members ADD CONSTRAINT workspace_members_role_check CHECK (role IN ('WRITER','REVIEWER','PRODUCER'));
UPDATE users SET auth_version=auth_version+1;
ALTER TABLE google_login_whitelist ADD COLUMN grant_revision bigint NOT NULL DEFAULT 1;
CREATE TABLE account_security_audit (
 id bigserial PRIMARY KEY, actor_id varchar(255) NOT NULL, user_id varchar(255) NOT NULL,
 action varchar(32) NOT NULL, old_role varchar(16), new_role varchar(16),
 old_status varchar(16), new_status varchar(16), created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE notification_outbox (
 id varchar(36) PRIMARY KEY, whitelist_id uuid NOT NULL REFERENCES google_login_whitelist(id),
 grant_revision bigint NOT NULL, event_type varchar(32) NOT NULL DEFAULT 'GOOGLE_ACCESS_GRANTED',
 template_version integer NOT NULL DEFAULT 1, recipient varchar(254) NOT NULL,
 provider varchar(16) NOT NULL CHECK (provider IN ('local','brevo')),
 status varchar(16) NOT NULL CHECK (status IN ('PENDING','SENDING','ACCEPTED','RETRY','FAILED','CANCELLED','UNKNOWN')),
 attempt_count integer NOT NULL DEFAULT 0, next_attempt_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 lease_until timestamp, claim_token varchar(36), provider_message_id varchar(255), error_code varchar(64),
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 accepted_at timestamp,
 resend_day date, resend_count integer NOT NULL DEFAULT 0,
 UNIQUE(whitelist_id,grant_revision,event_type)
);
CREATE INDEX idx_outbox_due ON notification_outbox(status,next_attempt_at);
CREATE INDEX idx_outbox_whitelist ON notification_outbox(whitelist_id,created_at DESC);
CREATE TABLE notification_audit (
 id bigserial PRIMARY KEY, event_id varchar(36) NOT NULL, actor_id varchar(255) NOT NULL,
 action varchar(32) NOT NULL, created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE notification_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE account_security_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON account_security_audit, notification_outbox, notification_audit FROM PUBLIC;
DO $$ DECLARE r text; BEGIN
 FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN
   EXECUTE format('REVOKE ALL ON account_security_audit,notification_outbox,notification_audit FROM %I',r);
  END IF;
 END LOOP;
END $$;
