-- READ ONLY. Run against the explicitly selected target only, after reviewing auth-v2 staging.
SELECT table_name,column_name,data_type,udt_name,is_nullable,column_default
FROM information_schema.columns
WHERE table_schema='public' AND table_name IN ('users','workspace_members','google_login_whitelist')
ORDER BY table_name,ordinal_position;
SELECT conrelid::regclass AS relation,conname,pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE conrelid IN ('users'::regclass,'workspace_members'::regclass,'google_login_whitelist'::regclass);
SELECT role::text,status::text,count(*) FROM users GROUP BY role::text,status::text;
SELECT role::text,is_leader,status::text,count(*) FROM workspace_members GROUP BY role::text,is_leader,status::text;
SELECT schemaname,tablename,policyname,roles,cmd,qual,with_check FROM pg_policies
WHERE tablename IN ('users','workspace_members','google_login_whitelist');
SELECT event_object_table,trigger_name,action_statement FROM information_schema.triggers
WHERE event_object_schema='public' AND event_object_table IN ('users','workspace_members');
SELECT grantee,table_name,privilege_type FROM information_schema.role_table_grants
WHERE table_schema='public' AND table_name IN ('users','workspace_members','google_login_whitelist');
-- Review views/functions too: a column rename alone cannot detect role assumptions in stored code.
SELECT schemaname,viewname,definition FROM pg_views
WHERE schemaname='public' AND (definition ILIKE '%user_role%' OR definition ILIKE '%users%');
SELECT n.nspname,p.proname,pg_get_functiondef(p.oid)
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prokind='f'
AND (p.prosrc ILIKE '%user_role%' OR p.prosrc ILIKE '%users%' OR p.prosrc ILIKE '%workspace_members%');
