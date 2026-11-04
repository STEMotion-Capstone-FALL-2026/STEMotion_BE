-- DRAFT ONLY: populate the reviewed mapping explicitly, in an isolated database first.
-- Do not derive usernames from email or silently approve existing google_id values.
BEGIN;
CREATE TEMP TABLE auth_username_mapping (user_id text PRIMARY KEY, username varchar(254) UNIQUE NOT NULL) ON COMMIT DROP;
-- INSERT INTO auth_username_mapping VALUES ('<existing-user-id>', '<reviewed-username>');
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM users u JOIN auth_username_mapping m ON m.user_id=u.id::text
     WHERE u.role='ADMIN' AND u.status='ACTIVE' AND u.password_hash IS NOT NULL) THEN
   RAISE EXCEPTION 'An active ADMIN with local password and reviewed username is required';
 END IF;
 IF EXISTS (SELECT 1 FROM auth_username_mapping m JOIN users u ON u.id::text=m.user_id
   WHERE m.username !~ '^[a-z0-9][a-z0-9._-]{2,31}$'
     AND NOT (m.username=lower(btrim(u.email)) AND m.username ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'))
 OR EXISTS (SELECT 1 FROM users u WHERE u.password_hash IS NOT NULL AND NOT EXISTS
   (SELECT 1 FROM auth_username_mapping m WHERE m.user_id=u.id::text))
 OR EXISTS (SELECT 1 FROM auth_username_mapping m WHERE NOT EXISTS
   (SELECT 1 FROM users u WHERE u.id::text=m.user_id)) THEN
   RAISE EXCEPTION 'Missing/invalid reviewed username mapping; no data was backfilled';
 END IF;
END $$;
UPDATE users u SET username=m.username FROM auth_username_mapping m WHERE u.id::text=m.user_id;
-- Add constraints AFTER every local credential has a reviewed username.
ALTER TABLE users ADD CONSTRAINT users_local_username CHECK (password_hash IS NULL OR username IS NOT NULL);
ALTER TABLE users ADD CONSTRAINT users_username_canonical CHECK (username IS NULL OR username ~ '^[a-z0-9][a-z0-9._-]{2,31}$'
 OR (email IS NOT NULL AND username=lower(btrim(email)) AND username ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'));
-- Legacy google_id approval/import is a separate explicit mapping. Never auto-whitelist users.email.
COMMIT;
