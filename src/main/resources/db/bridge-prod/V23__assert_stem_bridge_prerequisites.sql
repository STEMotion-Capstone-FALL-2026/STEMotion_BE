-- R2.1 REVIEW ONLY. NOT APPLIED. Do not run against a database in R2.
-- Candidate for PostgreSQL 17.11, public schema, auth already cut over / STEM fresh.
-- Requires a later approved transactional migration run. See BRIDGE_REVIEW_R2.md.
SET LOCAL search_path = pg_catalog;

-- Assert the route and both independent ledgers before creating objects.
DO $preconditions$
DECLARE
    table_name text;
    parent_oid regclass;
BEGIN
    IF current_setting('server_version_num')::integer / 10000 <> 17 THEN
        RAISE EXCEPTION 'Bridge requires PostgreSQL 17';
    END IF;
    -- PF07 candidate owner only. Verify the effective application role in R3.
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Bridge owner differs from the reviewed postgres candidate';
    END IF;

    IF to_regclass('public.flyway_schema_history') IS NULL
       OR to_regclass('supabase_migrations.schema_migrations') IS NULL THEN
        RAISE EXCEPTION 'Bridge prerequisite ledger missing';
    END IF;
    IF (SELECT count(*) FROM public.flyway_schema_history) <> 22
       OR NOT EXISTS (
            SELECT 1 FROM public.flyway_schema_history
            WHERE installed_rank = 1 AND version = '1'
              AND type = 'BASELINE' AND script = '<< Flyway Baseline >>'
              AND checksum IS NULL AND success
       )
       OR EXISTS (
            SELECT 1
            FROM (VALUES
                (2, '2', 'V2__promote_longtri_to_admin.sql', -909838945),
                (3, '3', 'V3__illustrated_explainer_scene.sql', 1238929282),
                (4, '4', 'V4__render_resolution_1440p.sql', 1782586610),
                (5, '5', 'V5__user_display_name.sql', -1405815381),
                (6, '6', 'V6__align_shared_scene_order.sql', -1025185796),
                (7, '7', 'V7__align_feedback_targets.sql', 376320671),
                (8, '8', 'V8__align_subject_and_user_status.sql', 1556243027),
                (9, '9', 'V9__workspace_invitation_tokens.sql', -513675999),
                (10, '10', 'V10__clip_artifacts.sql', 635493235),
                (11, '11', 'V11__explicit_review_decisions.sql', 931115790),
                (12, '12', 'V12__ai_request_tracking.sql', 1993775662),
                (13, '13', 'V13__publication_transition.sql', 209321070),
                (14, '14', 'V14__director_template_metadata.sql', -557826410),
                (15, '15', 'V15__composed_scene.sql', -1661809615),
                (16, '16', 'V16__member_status_inactive_value.sql', -1136403384),
                (17, '17', 'V17__admin_provisioned_membership.sql', 1091540199),
                (18, '18', 'V18__ai_pricing_provenance.sql', 1523296625),
                (19, '19', 'V19__production_composition.sql', -1393917377),
                (20, '20', 'V20__review_loop_and_theme.sql', 1086462783),
                (21, '21', 'V21__restore_v1_indexes.sql', -1246831619),
                (22, '22', 'V22__script_draft_revision.sql', 36395565)
            ) AS expected(rank, version, script, checksum)
            LEFT JOIN public.flyway_schema_history h ON h.installed_rank = expected.rank
            WHERE h.version IS DISTINCT FROM expected.version
               OR h.script IS DISTINCT FROM expected.script
               OR h.checksum IS DISTINCT FROM expected.checksum
               OR h.success IS DISTINCT FROM true
       ) THEN
        RAISE EXCEPTION 'Flyway history differs from the R1 V1 baseline and V2-V22 checksums';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM supabase_migrations.schema_migrations
        WHERE version = '20261102131936'
          AND name = 'stemotion_auth_roles_mail_legacy_username_cutover'
    ) THEN
        RAISE EXCEPTION 'Shared auth cutover ledger entry missing';
    END IF;

    FOREACH table_name IN ARRAY ARRAY[
        'users', 'workspaces', 'clip_projects', 'script_versions', 'scenes',
        'status_histories', 'render_jobs', 'compositions', 'clips', 'publications'
    ]
    LOOP
        parent_oid := to_regclass(format('public.%I', table_name));
        IF parent_oid IS NULL OR NOT EXISTS (
            SELECT 1
            FROM pg_attribute a
            JOIN pg_constraint pk ON pk.conrelid = a.attrelid
              AND pk.contype = 'p' AND pk.conkey = ARRAY[a.attnum]
            WHERE a.attrelid = parent_oid AND a.attname = 'id'
              AND a.atttypid = 'pg_catalog.uuid'::regtype
              AND a.attnotnull AND NOT a.attisdropped
        ) THEN
            RAISE EXCEPTION 'Expected public.%.id to be a single-column UUID primary key', table_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('username', 'character varying'::regtype, false),
            ('system_role', 'character varying'::regtype, true),
            ('auth_version', 'bigint'::regtype, true),
            ('version', 'bigint'::regtype, true),
            ('role', to_regtype('public.user_role'), false)
        ) AS expected(name, type_oid, required)
        LEFT JOIN pg_attribute a ON a.attrelid = 'public.users'::regclass
            AND a.attname = expected.name AND NOT a.attisdropped
        WHERE expected.type_oid IS NULL
           OR a.atttypid IS DISTINCT FROM expected.type_oid::oid
           OR a.attnotnull IS DISTINCT FROM expected.required
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.users'::regclass AND conname = 'users_system_role_check'
          AND contype = 'c' AND convalidated
    ) THEN
        RAISE EXCEPTION 'Auth column or constraint drift';
    END IF;
    FOREACH table_name IN ARRAY ARRAY[
        'account_security_audit', 'google_auth_challenges', 'google_identities',
        'google_login_whitelist', 'notification_audit', 'notification_outbox'
    ]
    LOOP
        IF to_regclass(format('public.%I', table_name)) IS NULL THEN
            RAISE EXCEPTION 'Auth relation missing: public.%', table_name;
        END IF;
    END LOOP;

    FOREACH table_name IN ARRAY ARRAY['stem_catalog', 'stem_briefs', 'stem_evidence', 'stem_settings', 'stem_assessments', 'stem_validation_runs', 'stem_validation_limits', 'stem_receipts', 'stem_audit', 'stem_p5_snapshots', 'stem_p5_compatibility']
    LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            RAISE EXCEPTION 'STEM object already present: public.%', table_name;
        END IF;
    END LOOP;
    IF to_regprocedure('public.stem_p5_append_only()') IS NOT NULL THEN
        RAISE EXCEPTION 'STEM append-only function already present';
    END IF;
END;
$preconditions$;
