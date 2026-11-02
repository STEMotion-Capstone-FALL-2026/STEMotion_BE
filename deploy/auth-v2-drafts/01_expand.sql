-- DRAFT ONLY. Not in Flyway locations. Do not run on the shared database.
-- Stop legacy provisioning before expansion/backfill. Preserve all existing user IDs.
BEGIN;
ALTER TABLE users ADD COLUMN username varchar(254);
ALTER TABLE users ADD COLUMN auth_version bigint NOT NULL DEFAULT 0;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
-- Legacy shared schemas can require a password even though the migration baseline does not.
-- Google-only accounts intentionally have neither a username nor a local password.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
CREATE UNIQUE INDEX uk_users_username ON users(username);
CREATE TABLE google_login_whitelist (
 id uuid PRIMARY KEY, email varchar(254) NOT NULL UNIQUE,
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
 created_by varchar(255) NOT NULL, updated_by varchar(255) NOT NULL,
 created_at timestamp NOT NULL, updated_at timestamp NOT NULL, revoked_at timestamp,
 version bigint NOT NULL DEFAULT 0,
 CONSTRAINT gmail_canonical CHECK (email = lower(btrim(email)) AND email LIKE '%@gmail.com')
);
-- The shared schema may use uuid or varchar for users.id. Derive the FK type instead of casting IDs.
DO $$ DECLARE user_id_type text; BEGIN
 SELECT format_type(atttypid, atttypmod) INTO user_id_type FROM pg_attribute
 WHERE attrelid='users'::regclass AND attname='id' AND NOT attisdropped;
 EXECUTE format('CREATE TABLE google_identities (
  id uuid PRIMARY KEY, user_id %s NOT NULL UNIQUE REFERENCES users(id),
  whitelist_id uuid NOT NULL UNIQUE REFERENCES google_login_whitelist(id),
  google_subject varchar(255) NOT NULL UNIQUE, email varchar(254) NOT NULL UNIQUE,
  linked_at timestamp NOT NULL, last_login_at timestamp,
  created_at timestamp NOT NULL, updated_at timestamp NOT NULL)', user_id_type);
END $$;
CREATE TABLE google_auth_challenges (
 id uuid PRIMARY KEY, nonce_hash varchar(64) NOT NULL, secret_hash varchar(64) NOT NULL,
 purpose varchar(16) NOT NULL CHECK (purpose IN ('LOGIN','LINK')), user_id varchar(255), auth_version bigint,
 expires_at timestamp NOT NULL, consumed_at timestamp, created_at timestamp NOT NULL, updated_at timestamp NOT NULL,
 CHECK ((purpose='LOGIN' AND user_id IS NULL AND auth_version IS NULL)
     OR (purpose='LINK' AND user_id IS NOT NULL AND auth_version IS NOT NULL))
);
CREATE INDEX idx_google_challenges_expiry ON google_auth_challenges(expires_at);
-- These are backend-only tables, not Supabase client/Data API resources.
-- The backend must use the reviewed table-owner/BYPASSRLS database role.
ALTER TABLE google_login_whitelist ENABLE ROW LEVEL SECURITY;
ALTER TABLE google_identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE google_auth_challenges ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON google_login_whitelist, google_identities, google_auth_challenges FROM PUBLIC;
DO $$ DECLARE client_role text; BEGIN
 FOREACH client_role IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname=client_role) THEN
   EXECUTE format('REVOKE ALL ON google_login_whitelist, google_identities, google_auth_challenges FROM %I',client_role);
  END IF;
 END LOOP;
END $$;
COMMIT;
