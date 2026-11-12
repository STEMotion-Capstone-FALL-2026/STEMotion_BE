-- R2.1 REVIEW ONLY. NOT APPLIED. Do not run against a database in R2.
-- Candidate for PostgreSQL 17.11, public schema, auth already cut over / STEM fresh.
-- Requires a later approved transactional migration run. See BRIDGE_REVIEW_R2.md.
SET LOCAL search_path = pg_catalog;

DO $owner$
BEGIN
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Bridge owner differs from the reviewed postgres candidate';
    END IF;
END;
$owner$;

CREATE TABLE public.stem_catalog (
    id varchar(36) PRIMARY KEY,
    kind varchar(30) NOT NULL CHECK (kind IN (
        'TOPIC', 'TOPIC_VERSION', 'SOURCE_EDITION', 'OUTCOME', 'PROFILE', 'POLICY', 'RUBRIC'
    )),
    logical_key varchar(128) NOT NULL,
    version_no integer NOT NULL CHECK (version_no > 0),
    revision integer NOT NULL DEFAULT 0 CHECK (revision >= 0),
    parent_id varchar(36) REFERENCES public.stem_catalog(id),
    source_id varchar(36) REFERENCES public.stem_catalog(id),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status = 'DRAFT'),
    data jsonb NOT NULL CHECK (jsonb_typeof(data) = 'object'),
    UNIQUE (kind, logical_key, version_no)
);

CREATE TABLE public.stem_briefs (
    id varchar(36) PRIMARY KEY,
    version_id uuid NOT NULL UNIQUE REFERENCES public.script_versions(id),
    revision integer NOT NULL DEFAULT 0 CHECK (revision >= 0),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status = 'DRAFT'),
    readiness varchar(30) NOT NULL DEFAULT 'INCOMPLETE',
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    policy_id varchar(36) REFERENCES public.stem_catalog(id),
    topic_id varchar(36) REFERENCES public.stem_catalog(id),
    copied_from varchar(36) REFERENCES public.stem_briefs(id)
);

CREATE TABLE public.stem_evidence (
    id varchar(36) PRIMARY KEY,
    brief_id varchar(36) NOT NULL REFERENCES public.stem_briefs(id),
    scene_id uuid REFERENCES public.scenes(id) ON DELETE SET NULL,
    original_scene_id varchar(128),
    task_key varchar(128),
    objective_key varchar(128),
    component varchar(1) NOT NULL CHECK (component IN ('S', 'T', 'E', 'M')),
    retired boolean NOT NULL DEFAULT false,
    explanation text,
    copied_from varchar(36),
    status varchar(20) NOT NULL DEFAULT 'NEEDS_REVIEW' CHECK (status = 'NEEDS_REVIEW'),
    CHECK (original_scene_id IS NOT NULL OR task_key IS NOT NULL)
);

CREATE TABLE public.stem_settings (
    workspace_id uuid PRIMARY KEY REFERENCES public.workspaces(id),
    revision integer NOT NULL CHECK (revision > 0),
    mode varchar(12) NOT NULL CHECK (mode IN ('OFF', 'ADVISORY')),
    policy_ids jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(policy_ids) = 'array'),
    default_policy_id varchar(36) REFERENCES public.stem_catalog(id),
    legacy_modes jsonb NOT NULL DEFAULT '[]'
);

CREATE TABLE public.stem_assessments (
    id varchar(36) PRIMARY KEY,
    version_id uuid NOT NULL REFERENCES public.script_versions(id),
    brief_id varchar(36) NOT NULL REFERENCES public.stem_briefs(id),
    decision_id uuid NOT NULL UNIQUE REFERENCES public.status_histories(id),
    actor_id uuid NOT NULL REFERENCES public.users(id),
    phase varchar(10) NOT NULL CHECK (phase IN ('SCRIPT', 'VIDEO')),
    brief_revision integer NOT NULL,
    script_revision integer NOT NULL,
    mode_revision integer NOT NULL,
    composition_revision integer,
    payload jsonb NOT NULL,
    verification_status varchar(20) NOT NULL DEFAULT 'UNVERIFIED'
        CHECK (verification_status = 'UNVERIFIED'),
    freshness varchar(30) NOT NULL DEFAULT 'NEEDS_REVIEW'
);

CREATE TABLE public.stem_validation_runs (
    id varchar(36) PRIMARY KEY,
    brief_id varchar(36) NOT NULL REFERENCES public.stem_briefs(id),
    phase varchar(10) NOT NULL CHECK (phase IN ('SCRIPT', 'VIDEO')),
    brief_revision integer NOT NULL,
    script_revision integer NOT NULL,
    mode_revision integer NOT NULL,
    findings jsonb NOT NULL,
    created_at timestamp NOT NULL DEFAULT current_timestamp
);

CREATE TABLE public.stem_validation_limits (
    actor_id uuid REFERENCES public.users(id),
    version_id uuid REFERENCES public.script_versions(id),
    bucket bigint NOT NULL,
    requests integer NOT NULL,
    PRIMARY KEY (actor_id, version_id)
);

CREATE TABLE public.stem_receipts (
    actor_id uuid REFERENCES public.users(id),
    operation varchar(30),
    target varchar(128) NOT NULL,
    request_key varchar(128),
    request_digest varchar(64) NOT NULL,
    response jsonb NOT NULL,
    expires_at timestamp NOT NULL,
    PRIMARY KEY (actor_id, operation, request_key)
);

CREATE TABLE public.stem_audit (
    id varchar(36) PRIMARY KEY,
    actor_id uuid REFERENCES public.users(id),
    target varchar(128) NOT NULL,
    event varchar(50) NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp NOT NULL DEFAULT current_timestamp
);

CREATE INDEX stem_evidence_brief_idx ON public.stem_evidence (brief_id);
CREATE INDEX stem_assessments_version_idx ON public.stem_assessments (version_id);

DO $access$
DECLARE
    table_name text;
    qualified_name text;
    relation_oid oid;
    owner_oid oid;
    grantee record;
    api_role record;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['stem_catalog', 'stem_briefs', 'stem_evidence', 'stem_settings', 'stem_assessments', 'stem_validation_runs', 'stem_validation_limits', 'stem_receipts', 'stem_audit']
    LOOP
        qualified_name := format('public.%I', table_name);
        relation_oid := to_regclass(qualified_name);
        SELECT relowner INTO STRICT owner_oid FROM pg_class WHERE oid = relation_oid;
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', qualified_name);
        EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE %s FROM PUBLIC', qualified_name);

        -- Remove object grants created by default ACLs, without editing the defaults.
        FOR grantee IN
            SELECT DISTINCT roles.rolname
            FROM pg_class c
            CROSS JOIN LATERAL aclexplode(c.relacl) AS acl
            JOIN pg_roles roles ON roles.oid = acl.grantee
            WHERE c.oid = relation_oid AND acl.grantee <> owner_oid
        LOOP
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE %s FROM %I',
                           qualified_name, grantee.rolname);
        END LOOP;

        IF EXISTS (
            SELECT 1 FROM pg_class c
            CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) AS acl
            WHERE c.oid = relation_oid AND acl.grantee <> owner_oid
        ) THEN
            RAISE EXCEPTION 'STEM access drift: % has a non-owner grant', qualified_name;
        END IF;

        FOR api_role IN SELECT oid, rolname FROM pg_roles
                        WHERE rolname IN ('anon', 'authenticated', 'service_role', 'authenticator')
        LOOP
            IF has_table_privilege(api_role.oid, relation_oid,
                    'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER, MAINTAIN')
               OR has_any_column_privilege(api_role.oid, relation_oid,
                    'SELECT, INSERT, UPDATE, REFERENCES') THEN
                RAISE EXCEPTION 'STEM access drift: % has effective privileges on %',
                    api_role.rolname, qualified_name;
            END IF;
        END LOOP;
    END LOOP;
END;
$access$;
