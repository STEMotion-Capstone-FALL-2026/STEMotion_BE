-- DRAFT ONLY. Populate only with explicitly reviewed legacy Google links and the approving ADMIN ID.
-- Run after expansion/backfill in the coordinated cutover, never while app provisioning is active.
BEGIN;
CREATE TEMP TABLE approved_legacy_google (
 user_id text PRIMARY KEY, email varchar(254) UNIQUE NOT NULL, subject varchar(255) UNIQUE NOT NULL,
 whitelist_id uuid UNIQUE NOT NULL, identity_id uuid UNIQUE NOT NULL, approved_by text NOT NULL
) ON COMMIT DROP;
-- INSERT INTO approved_legacy_google VALUES
-- ('<existing-user-id>','<reviewed@gmail.com>','<existing-google-sub>',
--  '<explicit-new-whitelist-uuid>','<explicit-new-identity-uuid>','<approving-admin-id>');
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM approved_legacy_google m LEFT JOIN users u ON u.id::text=m.user_id
     WHERE u.id IS NULL OR u.google_id IS DISTINCT FROM m.subject
        OR m.email <> lower(btrim(m.email)) OR m.email NOT LIKE '%@gmail.com'
        OR NOT EXISTS (SELECT 1 FROM users a WHERE a.id::text=m.approved_by AND a.role='ADMIN' AND a.status='ACTIVE')) THEN
  RAISE EXCEPTION 'Invalid/unapproved legacy identity mapping; import stopped';
 END IF;
END $$;
INSERT INTO google_login_whitelist(id,email,status,created_by,updated_by,created_at,updated_at,version)
 SELECT whitelist_id,email,'ACTIVE',approved_by,approved_by,now(),now(),0 FROM approved_legacy_google;
-- Cast by joining the actual users PK instead of guessing uuid versus varchar.
INSERT INTO google_identities(id,user_id,whitelist_id,google_subject,email,linked_at,created_at,updated_at)
 SELECT m.identity_id,u.id,m.whitelist_id,m.subject,m.email,now(),now(),now()
 FROM approved_legacy_google m JOIN users u ON u.id::text=m.user_id;
-- Unique violations are intentionally fatal; never overwrite an existing owner or authorization.
COMMIT;
