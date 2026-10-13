-- 2026-10-11T08:39:25.242992400Z backup
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;

-- 2026-10-11T08:39:25.593319100Z backup
SHOW transaction_read_only;

-- 2026-10-11T08:39:25.924673600Z backup
SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;

-- 2026-10-11T08:39:26.261865700Z backup
SELECT t.typname, e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid=t.oid JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' ORDER BY t.typname,e.enumsortorder;

-- 2026-10-11T08:39:26.606722200Z backup
SELECT sequencename,data_type,start_value,min_value,max_value,increment_by,cycle,cache_size FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename;

-- 2026-10-11T08:39:26.941690200Z backup
SELECT pg_get_functiondef(p.oid) AS definition FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prokind IN ('f','p') ORDER BY p.oid;

-- 2026-10-11T08:39:27.338600900Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='ai_requests' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:27.675250800Z backup
SELECT row_to_json(t)::text AS row_data FROM public."ai_requests" t;

-- 2026-10-11T08:39:28.011694600Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='asset_media' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:28.341682300Z backup
SELECT row_to_json(t)::text AS row_data FROM public."asset_media" t;

-- 2026-10-11T08:39:28.676317300Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='clip_projects' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:29.008430600Z backup
SELECT row_to_json(t)::text AS row_data FROM public."clip_projects" t;

-- 2026-10-11T08:39:29.341822700Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='clips' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:29.674336Z backup
SELECT row_to_json(t)::text AS row_data FROM public."clips" t;

-- 2026-10-11T08:39:30.002206800Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='compositions' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:30.339532500Z backup
SELECT row_to_json(t)::text AS row_data FROM public."compositions" t;

-- 2026-10-11T08:39:30.672940200Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='feedback_comments' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:31.005033Z backup
SELECT row_to_json(t)::text AS row_data FROM public."feedback_comments" t;

-- 2026-10-11T08:39:31.338697200Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='flyway_schema_history' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:31.670408700Z backup
SELECT row_to_json(t)::text AS row_data FROM public."flyway_schema_history" t;

-- 2026-10-11T08:39:32.005305700Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='publications' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:32.337369200Z backup
SELECT row_to_json(t)::text AS row_data FROM public."publications" t;

-- 2026-10-11T08:39:32.672042200Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='publish_records' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:33.004195300Z backup
SELECT row_to_json(t)::text AS row_data FROM public."publish_records" t;

-- 2026-10-11T08:39:33.335232500Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='remotion_templates' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:33.667212Z backup
SELECT row_to_json(t)::text AS row_data FROM public."remotion_templates" t;

-- 2026-10-11T08:39:33.992511800Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='render_jobs' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:34.322605600Z backup
SELECT row_to_json(t)::text AS row_data FROM public."render_jobs" t;

-- 2026-10-11T08:39:34.662728800Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='review_decisions' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:34.993985700Z backup
SELECT row_to_json(t)::text AS row_data FROM public."review_decisions" t;

-- 2026-10-11T08:39:35.326672200Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='scene_assets' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:35.641621700Z backup
SELECT row_to_json(t)::text AS row_data FROM public."scene_assets" t;

-- 2026-10-11T08:39:35.988030500Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='scenes' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:36.319677200Z backup
SELECT row_to_json(t)::text AS row_data FROM public."scenes" t;

-- 2026-10-11T08:39:36.652307200Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='script_versions' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:36.981599800Z backup
SELECT row_to_json(t)::text AS row_data FROM public."script_versions" t;

-- 2026-10-11T08:39:37.315831100Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='status_histories' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:37.641606900Z backup
SELECT row_to_json(t)::text AS row_data FROM public."status_histories" t;

-- 2026-10-11T08:39:37.972368500Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='templates' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:38.312224900Z backup
SELECT row_to_json(t)::text AS row_data FROM public."templates" t;

-- 2026-10-11T08:39:38.641512Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='users' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:38.973176600Z backup
SELECT row_to_json(t)::text AS row_data FROM public."users" t;

-- 2026-10-11T08:39:39.311615Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='workspace_members' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:39.641568400Z backup
SELECT row_to_json(t)::text AS row_data FROM public."workspace_members" t;

-- 2026-10-11T08:39:39.975092500Z backup
SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS sql_type,a.attnotnull,pg_get_expr(d.adbin,d.adrelid) AS default_expr,a.attidentity,a.attgenerated FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND r.relname='workspaces' AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum;

-- 2026-10-11T08:39:40.306686Z backup
SELECT row_to_json(t)::text AS row_data FROM public."workspaces" t;

-- 2026-10-11T08:39:40.638327700Z backup
SELECT r.relname AS table_name,co.conname,co.contype,pg_get_constraintdef(co.oid) AS definition FROM pg_constraint co JOIN pg_class r ON r.oid=co.conrelid JOIN pg_namespace n ON n.oid=r.relnamespace WHERE n.nspname='public' ORDER BY CASE WHEN co.contype='f' THEN 1 ELSE 0 END,r.relname,co.conname;

-- 2026-10-11T08:39:40.991810600Z backup
SELECT pg_get_indexdef(i.indexrelid) AS definition FROM pg_index i JOIN pg_class r ON r.oid=i.indrelid JOIN pg_namespace n ON n.oid=r.relnamespace WHERE n.nspname='public' AND NOT EXISTS (SELECT 1 FROM pg_constraint co WHERE co.conindid=i.indexrelid) ORDER BY i.indexrelid;

-- 2026-10-11T08:39:41.329471200Z backup
SELECT pg_get_triggerdef(t.oid) AS definition FROM pg_trigger t JOIN pg_class r ON r.oid=t.tgrelid JOIN pg_namespace n ON n.oid=r.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal ORDER BY t.oid;

-- 2026-10-11T08:39:41.667865800Z backup
SELECT viewname,definition FROM pg_views WHERE schemaname='public' ORDER BY viewname;

-- 2026-10-11T08:39:41.994244600Z backup
SELECT * FROM pg_policies WHERE schemaname='public';

-- 2026-10-11T08:39:42.333142600Z backup
SELECT r.relname,r.relrowsecurity,r.relforcerowsecurity FROM pg_class r JOIN pg_namespace n ON n.oid=r.relnamespace WHERE n.nspname='public' AND r.relkind IN ('r','p');

-- 2026-10-11T08:39:42.664759200Z backup
SELECT * FROM information_schema.table_privileges WHERE table_schema='public';

-- 2026-10-11T08:39:43.333408200Z backup
SELECT extname,extversion,n.nspname AS schema_name FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace;

-- 2026-10-11T08:39:43.669830100Z backup
SELECT version,description,script,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank;

-- 2026-10-11T08:39:44.000688600Z backup
COMMIT;

-- 2026-10-11T08:40:13.006784Z apply
BEGIN;

-- 2026-10-11T08:40:13.364744900Z apply
SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='45s'; SET LOCAL search_path=public;

-- 2026-10-11T08:40:13.707435300Z apply
LOCK TABLE public.remotion_templates IN ACCESS EXCLUSIVE MODE; LOCK TABLE public.flyway_schema_history IN EXCLUSIVE MODE;

-- 2026-10-11T08:40:14.041674800Z apply
SELECT version,success FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;

-- 2026-10-11T08:40:14.401583Z apply
SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='remotion_templates' AND column_name IN ('subject','asset_pack','slots');

-- 2026-10-11T08:40:14.757357100Z apply
-- Additive metadata only. Existing scene/template API fields remain unchanged.
ALTER TABLE remotion_templates ADD COLUMN subject VARCHAR(32);
ALTER TABLE remotion_templates ADD COLUMN asset_pack VARCHAR(128);
ALTER TABLE remotion_templates ADD COLUMN slots JSONB;
ALTER TABLE remotion_templates ADD CONSTRAINT ck_template_slots_object
    CHECK (slots IS NULL OR jsonb_typeof(slots) = 'object');


-- 2026-10-11T08:40:15.139054200Z apply
INSERT INTO public.flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) SELECT COALESCE(MAX(installed_rank),0)+1,'14','director template metadata','SQL','V14__director_template_metadata.sql',-557826410,current_user,375,true FROM public.flyway_schema_history;

-- 2026-10-11T08:40:15.485151500Z apply
COMMIT;

-- 2026-10-11T08:40:26.381214300Z verify
BEGIN READ ONLY;

-- 2026-10-11T08:40:26.734868900Z verify
SELECT column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='remotion_templates' AND column_name IN ('subject','asset_pack','slots') ORDER BY column_name;

-- 2026-10-11T08:40:27.087650500Z verify
SELECT conname,pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid='public.remotion_templates'::regclass AND conname='ck_template_slots_object';

-- 2026-10-11T08:40:27.432817600Z verify
SELECT version,script,checksum,success,installed_on FROM public.flyway_schema_history WHERE version='14';

-- 2026-10-11T08:40:27.780111Z verify
COMMIT;
