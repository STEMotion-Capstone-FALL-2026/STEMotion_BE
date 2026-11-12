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

CREATE TABLE public.stem_p5_snapshots (
    id varchar(36) PRIMARY KEY,
    stage varchar(20) NOT NULL
        CHECK (stage IN ('SUBMISSION', 'APPROVAL', 'RENDER_INPUT', 'CLIP', 'PUBLICATION')),
    project_id uuid NOT NULL REFERENCES public.clip_projects(id),
    version_id uuid NOT NULL REFERENCES public.script_versions(id),
    workspace_id uuid NOT NULL REFERENCES public.workspaces(id),
    actor_id uuid REFERENCES public.users(id),
    composition_id uuid REFERENCES public.compositions(id),
    composition_revision integer CHECK (composition_revision >= 1),
    job_id uuid REFERENCES public.render_jobs(id),
    clip_id uuid REFERENCES public.clips(id),
    publication_id uuid REFERENCES public.publications(id),
    decision_id uuid REFERENCES public.status_histories(id),
    assessment_id varchar(36) REFERENCES public.stem_assessments(id),
    parent_id varchar(36) REFERENCES public.stem_p5_snapshots(id),
    approval_id varchar(36) REFERENCES public.stem_p5_snapshots(id),
    semantic_hash varchar(64) NOT NULL CHECK (semantic_hash ~ '^[0-9a-f]{64}$'),
    contract_hash varchar(64) NOT NULL CHECK (contract_hash ~ '^[0-9a-f]{64}$'),
    input_hash varchar(64) CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    classification varchar(12) NOT NULL CHECK (classification IN ('CLASSIFIED', 'UNKNOWN')),
    assurance varchar(24) NOT NULL CHECK (assurance IN ('UNKNOWN', 'BINDING_ONLY')),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'
        AND octet_length(payload::text) <= 16777216),
    frozen_input jsonb CHECK (jsonb_typeof(frozen_input) = 'object'
        AND octet_length(frozen_input::text) <= 8388608),
    dedupe_key varchar(64) NOT NULL UNIQUE CHECK (dedupe_key ~ '^[0-9a-f]{64}$'),
    created_at timestamp NOT NULL DEFAULT current_timestamp,
    CHECK (stage <> 'APPROVAL' OR decision_id IS NOT NULL),
    CHECK (stage <> 'RENDER_INPUT' OR (job_id IS NOT NULL AND frozen_input IS NOT NULL AND input_hash IS NOT NULL)),
    CHECK (stage <> 'CLIP' OR (clip_id IS NOT NULL AND job_id IS NOT NULL)),
    CHECK (stage <> 'PUBLICATION' OR (clip_id IS NOT NULL AND publication_id IS NOT NULL))
);

CREATE UNIQUE INDEX stem_p5_job_unique ON public.stem_p5_snapshots (job_id) WHERE stage = 'RENDER_INPUT';
CREATE UNIQUE INDEX stem_p5_clip_unique ON public.stem_p5_snapshots (clip_id) WHERE stage = 'CLIP';
CREATE UNIQUE INDEX stem_p5_publication_unique ON public.stem_p5_snapshots (publication_id) WHERE stage = 'PUBLICATION';
CREATE UNIQUE INDEX stem_p5_decision_unique ON public.stem_p5_snapshots (decision_id) WHERE stage = 'APPROVAL';
CREATE INDEX stem_p5_target_idx ON public.stem_p5_snapshots (project_id, version_id, stage);

CREATE TABLE public.stem_p5_compatibility (
    id varchar(36) PRIMARY KEY,
    approval_id varchar(36) NOT NULL REFERENCES public.stem_p5_snapshots(id),
    before_id varchar(36) NOT NULL REFERENCES public.stem_p5_snapshots(id),
    after_id varchar(36) NOT NULL REFERENCES public.stem_p5_snapshots(id),
    proof_version varchar(128) NOT NULL,
    result varchar(16) NOT NULL CHECK (result IN ('COMPATIBLE', 'INCOMPATIBLE', 'UNKNOWN')),
    proof jsonb NOT NULL CHECK (jsonb_typeof(proof) = 'object'
        AND octet_length(proof::text) <= 65536),
    created_at timestamp NOT NULL DEFAULT current_timestamp,
    UNIQUE (approval_id, before_id, after_id, proof_version)
);

CREATE FUNCTION public.stem_p5_append_only() RETURNS trigger
    LANGUAGE plpgsql SECURITY INVOKER SET search_path = pg_catalog AS $append_only$
BEGIN
    RAISE EXCEPTION 'STEM snapshot/proof is append-only' USING ERRCODE = '23514';
END;
$append_only$;

CREATE TRIGGER stem_p5_snapshots_immutable BEFORE UPDATE OR DELETE ON public.stem_p5_snapshots
    FOR EACH ROW EXECUTE FUNCTION public.stem_p5_append_only();
CREATE TRIGGER stem_p5_proofs_immutable BEFORE UPDATE OR DELETE ON public.stem_p5_compatibility
    FOR EACH ROW EXECUTE FUNCTION public.stem_p5_append_only();
CREATE TRIGGER stem_p5_snapshots_no_truncate BEFORE TRUNCATE ON public.stem_p5_snapshots
    FOR EACH STATEMENT EXECUTE FUNCTION public.stem_p5_append_only();
CREATE TRIGGER stem_p5_proofs_no_truncate BEFORE TRUNCATE ON public.stem_p5_compatibility
    FOR EACH STATEMENT EXECUTE FUNCTION public.stem_p5_append_only();

REVOKE ALL PRIVILEGES ON FUNCTION public.stem_p5_append_only() FROM PUBLIC;
DO $function_access$
DECLARE
    grantee record;
    function_oid oid := 'public.stem_p5_append_only()'::regprocedure;
BEGIN
    FOR grantee IN
        SELECT DISTINCT roles.rolname
        FROM pg_proc p
        CROSS JOIN LATERAL aclexplode(p.proacl) AS acl
        JOIN pg_roles roles ON roles.oid = acl.grantee
        WHERE p.oid = function_oid AND acl.grantee <> p.proowner
    LOOP
        EXECUTE format('REVOKE ALL PRIVILEGES ON FUNCTION public.stem_p5_append_only() FROM %I',
                       grantee.rolname);
    END LOOP;
    IF EXISTS (
        SELECT 1 FROM pg_proc p
        CROSS JOIN LATERAL aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) AS acl
        WHERE p.oid = function_oid AND acl.grantee <> p.proowner
    ) OR EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname IN ('anon', 'authenticated', 'service_role', 'authenticator')
          AND has_function_privilege(oid, function_oid, 'EXECUTE')
    ) THEN
        RAISE EXCEPTION 'STEM append-only function privileges differ from the reviewed owner-only ACL';
    END IF;
END;
$function_access$;

DO $access$
DECLARE
    table_name text;
    qualified_name text;
    relation_oid oid;
    owner_oid oid;
    grantee record;
    api_role record;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['stem_p5_snapshots', 'stem_p5_compatibility']
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
