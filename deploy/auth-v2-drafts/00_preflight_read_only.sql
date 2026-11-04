-- PREPARED ONLY, NOT EXECUTED. Run on an approved isolated copy first.
-- Never select password hashes, Google tokens, environment secrets or JWTs.
SELECT count(*) AS users,
       count(*) FILTER (WHERE password_hash IS NOT NULL) AS local_credentials,
       count(*) FILTER (WHERE google_id IS NOT NULL) AS legacy_google_links,
       count(*) FILTER (WHERE role='ADMIN' AND status='ACTIVE' AND password_hash IS NOT NULL) AS local_active_admins
FROM users;
SELECT lower(btrim(email)) AS normalized_email, count(*)
FROM users WHERE email IS NOT NULL GROUP BY lower(btrim(email)) HAVING count(*)>1;
SELECT google_id, count(*) FROM users WHERE google_id IS NOT NULL GROUP BY google_id HAVING count(*)>1;
SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name='users';
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='users'::regclass;
SELECT indexname,indexdef FROM pg_indexes WHERE tablename='users';
SELECT current_user,rolbypassrls FROM pg_roles WHERE rolname=current_user;
SELECT schemaname,tablename,tableowner,rowsecurity FROM pg_tables WHERE tablename='users';
SELECT grantee,privilege_type FROM information_schema.role_table_grants WHERE table_name='users';
-- Review usernames by stable user ID. Contact email is not the new login identifier.
SELECT id,full_name,role,status, password_hash IS NOT NULL AS has_local_password,
       google_id IS NOT NULL AS has_legacy_google_link
FROM users ORDER BY role,full_name;
