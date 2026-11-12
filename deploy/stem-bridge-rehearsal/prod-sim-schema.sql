-- prod-sim-schema.sql
-- Production schema simulation for local rehearsal of V23–V26 bridge migrations.
-- WARNING: Local simulation only. Does NOT replace R3 rehearsal on a production schema-only dump.
-- NEVER execute against production or any database containing real data.

CREATE SCHEMA IF NOT EXISTS supabase_migrations;

CREATE TABLE IF NOT EXISTS supabase_migrations.schema_migrations (
    version text NOT NULL PRIMARY KEY,
    name text,
    statements text[]
);

INSERT INTO supabase_migrations.schema_migrations (version, name)
VALUES ('20261102131936', 'stemotion_auth_roles_mail_legacy_username_cutover')
ON CONFLICT (version) DO UPDATE SET name = EXCLUDED.name;

CREATE TABLE IF NOT EXISTS public.flyway_schema_history (
    installed_rank integer NOT NULL PRIMARY KEY,
    version varchar(50),
    description varchar(200) NOT NULL,
    type varchar(20) NOT NULL,
    script varchar(1000) NOT NULL,
    checksum integer,
    installed_by varchar(100) NOT NULL DEFAULT current_user,
    installed_on timestamp NOT NULL DEFAULT now(),
    execution_time integer NOT NULL,
    success boolean NOT NULL
);

INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES
    (1, '1', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'postgres', 0, true),
    (2, '2', 'promote longtri to admin', 'SQL', 'V2__promote_longtri_to_admin.sql', -909838945, 'postgres', 10, true),
    (3, '3', 'illustrated explainer scene', 'SQL', 'V3__illustrated_explainer_scene.sql', 1238929282, 'postgres', 10, true),
    (4, '4', 'render resolution 1440p', 'SQL', 'V4__render_resolution_1440p.sql', 1782586610, 'postgres', 10, true),
    (5, '5', 'user display name', 'SQL', 'V5__user_display_name.sql', -1405815381, 'postgres', 10, true),
    (6, '6', 'align shared scene order', 'SQL', 'V6__align_shared_scene_order.sql', -1025185796, 'postgres', 10, true),
    (7, '7', 'align feedback targets', 'SQL', 'V7__align_feedback_targets.sql', 376320671, 'postgres', 10, true),
    (8, '8', 'align subject and user status', 'SQL', 'V8__align_subject_and_user_status.sql', 1556243027, 'postgres', 10, true),
    (9, '9', 'workspace invitation tokens', 'SQL', 'V9__workspace_invitation_tokens.sql', -513675999, 'postgres', 10, true),
    (10, '10', 'clip artifacts', 'SQL', 'V10__clip_artifacts.sql', 635493235, 'postgres', 10, true),
    (11, '11', 'explicit review decisions', 'SQL', 'V11__explicit_review_decisions.sql', 931115790, 'postgres', 10, true),
    (12, '12', 'ai request tracking', 'SQL', 'V12__ai_request_tracking.sql', 1993775662, 'postgres', 10, true),
    (13, '13', 'publication transition', 'SQL', 'V13__publication_transition.sql', 209321070, 'postgres', 10, true),
    (14, '14', 'director template metadata', 'SQL', 'V14__director_template_metadata.sql', -557826410, 'postgres', 10, true),
    (15, '15', 'composed scene', 'SQL', 'V15__composed_scene.sql', -1661809615, 'postgres', 10, true),
    (16, '16', 'member status inactive value', 'SQL', 'V16__member_status_inactive_value.sql', -1136403384, 'postgres', 10, true),
    (17, '17', 'admin provisioned membership', 'SQL', 'V17__admin_provisioned_membership.sql', 1091540199, 'postgres', 10, true),
    (18, '18', 'ai pricing provenance', 'SQL', 'V18__ai_pricing_provenance.sql', 1523296625, 'postgres', 10, true),
    (19, '19', 'production composition', 'SQL', 'V19__production_composition.sql', -1393917377, 'postgres', 10, true),
    (20, '20', 'review loop and theme', 'SQL', 'V20__review_loop_and_theme.sql', 1086462783, 'postgres', 10, true),
    (21, '21', 'restore v1 indexes', 'SQL', 'V21__restore_v1_indexes.sql', -1246831619, 'postgres', 10, true),
    (22, '22', 'script draft revision', 'SQL', 'V22__script_draft_revision.sql', 36395565, 'postgres', 10, true)
ON CONFLICT (installed_rank) DO UPDATE SET
    version = EXCLUDED.version,
    script = EXCLUDED.script,
    checksum = EXCLUDED.checksum,
    success = EXCLUDED.success;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role' AND typnamespace = 'public'::regnamespace) THEN
        CREATE TYPE public.user_role AS ENUM ('USER', 'ADMIN', 'SYSTEM');
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS public.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username character varying,
    system_role character varying NOT NULL DEFAULT 'USER',
    auth_version bigint NOT NULL DEFAULT 1,
    version bigint NOT NULL DEFAULT 1,
    role public.user_role,
    CONSTRAINT users_system_role_check CHECK (system_role IN ('USER', 'ADMIN', 'SYSTEM'))
);

CREATE TABLE IF NOT EXISTS public.workspaces (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL
);

CREATE TABLE IF NOT EXISTS public.clip_projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid REFERENCES public.workspaces(id)
);

CREATE TABLE IF NOT EXISTS public.script_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid REFERENCES public.clip_projects(id)
);

CREATE TABLE IF NOT EXISTS public.scenes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id uuid REFERENCES public.script_versions(id)
);

CREATE TABLE IF NOT EXISTS public.status_histories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.render_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.compositions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.clips (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.publications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.account_security_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.google_auth_challenges (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.google_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.google_login_whitelist (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.notification_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.notification_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);
