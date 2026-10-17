# API Audit Evidence — 2026-10-17

Nguồn bổ trợ cho [checklist](API_AUDIT_CHECKLIST_2026-10-17.md). Chỉ source trong checkout BE main c7fbc49, không phải schema dump production. DDL liệt kê nguyên văn để giữ đủ columns, CHECK, FK, UNIQUE, indexes và trigger, kể cả nhánh baseline; không chạy SQL.

## 1. DDL V1–V19 (đã đọc từng file)

### V1__initial_schema.sql

Nguồn: [V1__initial_schema.sql](../STEMotion_BE/src/main/resources/db/migration/V1__initial_schema.sql)

```sql
-- STEMotion initial schema.
--
-- Enum-backed columns are plain varchar with an explicit CHECK constraint.
-- Hibernate would generate those constraints on first create but never alter
-- them afterwards, so adding an enum value used to break inserts at runtime.
-- Owning them here means a new value arrives as its own migration.

CREATE TABLE users (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    full_name               VARCHAR(255) NOT NULL,
    email                   VARCHAR(255) NOT NULL UNIQUE,
    google_id               VARCHAR(255) UNIQUE,
    password_hash           VARCHAR(255),
    role                    VARCHAR(20)  NOT NULL
        CONSTRAINT users_role_check CHECK (role IN ('WRITER', 'REVIEWER', 'PRODUCER', 'ADMIN')),
    status                  VARCHAR(20)  NOT NULL
        CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'BANNED'))
);

CREATE TABLE workspaces (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    department              VARCHAR(255),
    created_by              VARCHAR(36)  REFERENCES users (id)
);

CREATE TABLE workspace_members (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    workspace_id            VARCHAR(36)  NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id                 VARCHAR(36)  REFERENCES users (id),
    invited_email           VARCHAR(255) NOT NULL,
    role                    VARCHAR(20)  NOT NULL
        CONSTRAINT workspace_members_role_check CHECK (role IN ('WRITER', 'REVIEWER', 'PRODUCER', 'ADMIN')),
    status                  VARCHAR(20)  NOT NULL
        CONSTRAINT workspace_members_status_check CHECK (status IN ('ACTIVE', 'INVITED')),
    CONSTRAINT uk_workspace_member_email UNIQUE (workspace_id, invited_email)
);

CREATE TABLE clip_projects (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    subject                 VARCHAR(30)  NOT NULL
        CONSTRAINT clip_projects_subject_check
        CHECK (subject IN ('Math', 'Physics', 'Chemistry', 'Biology', 'ComputerScience')),
    grade_level             VARCHAR(255),
    fps                     INTEGER      NOT NULL,
    total_duration_seconds  INTEGER      NOT NULL,
    script_status           VARCHAR(30)  NOT NULL
        CONSTRAINT clip_projects_script_status_check
        CHECK (script_status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'CHANGE_REQUESTED')),
    video_status            VARCHAR(30)  NOT NULL
        CONSTRAINT clip_projects_video_status_check
        CHECK (video_status IN ('NOT_RENDERED', 'RENDERING', 'IN_QA', 'APPROVED', 'PUBLISHED')),
    video_url               VARCHAR(500),
    archived                BOOLEAN      NOT NULL DEFAULT FALSE,
    owner_id                VARCHAR(36)  NOT NULL REFERENCES users (id),
    workspace_id            VARCHAR(36)  NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE
);

CREATE INDEX idx_clip_projects_workspace ON clip_projects (workspace_id, created_at DESC);
CREATE INDEX idx_clip_projects_owner ON clip_projects (owner_id, created_at DESC);
CREATE INDEX idx_clip_projects_script_status ON clip_projects (script_status);

CREATE TABLE script_versions (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    version_no              INTEGER      NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    summary                 TEXT,
    status                  VARCHAR(30)  NOT NULL
        CONSTRAINT script_versions_status_check
        CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'CHANGE_REQUESTED')),
    review_note             TEXT,
    is_current              BOOLEAN      NOT NULL DEFAULT TRUE,
    project_id              VARCHAR(36)  NOT NULL REFERENCES clip_projects (id) ON DELETE CASCADE,
    created_by              VARCHAR(36)  NOT NULL REFERENCES users (id),
    CONSTRAINT uk_script_version_no UNIQUE (project_id, version_no)
);

CREATE INDEX idx_script_versions_project ON script_versions (project_id, version_no DESC);

CREATE TABLE scenes (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    order_no                INTEGER      NOT NULL,
    scene_type              VARCHAR(40)  NOT NULL
        CONSTRAINT scenes_scene_type_check CHECK (scene_type IN (
            'TITLE_HERO', 'MATH_FORMULA', 'DIAGRAM_EXPLAINER', 'DATA_CHART',
            'ALGORITHM_WALKTHROUGH', 'STEM_QUIZ', 'OUTRO',
            'CHEMICAL_REACTION', 'COMPARISON_SPLIT', 'PROCESS_TIMELINE', 'GEOMETRY_SPACE'
        )),
    title                   VARCHAR(255) NOT NULL,
    narration               TEXT,
    duration_in_frames      INTEGER      NOT NULL,
    props                   JSONB,
    version_id              VARCHAR(36)  NOT NULL REFERENCES script_versions (id) ON DELETE CASCADE
);

CREATE INDEX idx_scenes_version ON scenes (version_id, order_no);

CREATE TABLE feedback_comments (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    project_id              VARCHAR(36)  NOT NULL REFERENCES clip_projects (id) ON DELETE CASCADE,
    scene_id                VARCHAR(36)  REFERENCES scenes (id) ON DELETE CASCADE,
    author_id               VARCHAR(36)  NOT NULL REFERENCES users (id),
    timestamp_sec           DOUBLE PRECISION,
    content                 TEXT         NOT NULL,
    tag                     VARCHAR(255),
    status                  VARCHAR(20)  NOT NULL
        CONSTRAINT feedback_comments_status_check CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_feedback_project ON feedback_comments (project_id, timestamp_sec);
CREATE INDEX idx_feedback_scene ON feedback_comments (scene_id);

CREATE TABLE remotion_templates (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    name                    VARCHAR(255) NOT NULL UNIQUE,
    category                VARCHAR(255),
    props_schema            TEXT,
    preview_url             VARCHAR(500),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE render_jobs (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    project_id              VARCHAR(36)  NOT NULL REFERENCES clip_projects (id) ON DELETE CASCADE,
    version_id              VARCHAR(36)  NOT NULL REFERENCES script_versions (id),
    requested_by            VARCHAR(36)  NOT NULL REFERENCES users (id),
    external_job_id         VARCHAR(255),
    status                  VARCHAR(20)  NOT NULL
        CONSTRAINT render_jobs_status_check
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    progress                INTEGER      NOT NULL DEFAULT 0,
    stage                   VARCHAR(255),
    video_url               VARCHAR(500),
    error_message           TEXT,
    resolution              VARCHAR(20)  NOT NULL
        CONSTRAINT render_jobs_resolution_check
        CHECK (resolution IN ('RES_720P', 'RES_1080P', 'RES_4K')),
    fps                     INTEGER      NOT NULL,
    tts_voice               VARCHAR(255),
    attempt_count           INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_render_jobs_project ON render_jobs (project_id, created_at DESC);
CREATE INDEX idx_render_jobs_external ON render_jobs (external_job_id);

CREATE TABLE publish_records (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    project_id              VARCHAR(36)  NOT NULL REFERENCES clip_projects (id) ON DELETE CASCADE,
    published_by            VARCHAR(36)  NOT NULL REFERENCES users (id),
    targets                 VARCHAR(255) NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    description             TEXT,
    lms_embed_code          TEXT,
    youtube_url             VARCHAR(500)
);

CREATE INDEX idx_publish_project ON publish_records (project_id, created_at DESC);

CREATE TABLE status_histories (
    id                      VARCHAR(36)  PRIMARY KEY,
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    project_id              VARCHAR(36)  NOT NULL REFERENCES clip_projects (id) ON DELETE CASCADE,
    from_status             VARCHAR(30)
        CONSTRAINT status_histories_from_check CHECK (from_status IN (
            'DRAFT', 'SCRIPT_REVIEW', 'APPROVED', 'IN_PRODUCTION',
            'CLIP_REVIEW', 'PUBLISHED', 'ARCHIVED'
        )),
    to_status               VARCHAR(30)  NOT NULL
        CONSTRAINT status_histories_to_check CHECK (to_status IN (
            'DRAFT', 'SCRIPT_REVIEW', 'APPROVED', 'IN_PRODUCTION',
            'CLIP_REVIEW', 'PUBLISHED', 'ARCHIVED'
        )),
    note                    TEXT,
    changed_by              VARCHAR(36)  NOT NULL REFERENCES users (id)
);

CREATE INDEX idx_status_histories_project ON status_histories (project_id, created_at DESC);
```

### V2__promote_longtri_to_admin.sql

Nguồn: [V2__promote_longtri_to_admin.sql](../STEMotion_BE/src/main/resources/db/migration/V2__promote_longtri_to_admin.sql)

```sql
-- Promote Long Tri to ADMIN so the developer session can navigate all studios and test permissions without 403
UPDATE users
SET role = 'ADMIN'
WHERE email = 'longtri@stemotion.vn';

UPDATE workspace_members
SET role = 'ADMIN'
WHERE invited_email = 'longtri@stemotion.vn';
```

### V3__illustrated_explainer_scene.sql

Nguồn: [V3__illustrated_explainer_scene.sql](../STEMotion_BE/src/main/resources/db/migration/V3__illustrated_explainer_scene.sql)

```sql
-- Flat-explainer scene: STEM icons composed as an illustration on a moving
-- backdrop. Its props (headline, caption, ambience, layout, icons) live in the
-- existing JSONB column, so only the scene type check needs to widen.

ALTER TABLE scenes DROP CONSTRAINT scenes_scene_type_check;

ALTER TABLE scenes ADD CONSTRAINT scenes_scene_type_check CHECK (scene_type IN (
    'TITLE_HERO', 'MATH_FORMULA', 'DIAGRAM_EXPLAINER', 'DATA_CHART',
    'ALGORITHM_WALKTHROUGH', 'STEM_QUIZ', 'OUTRO',
    'CHEMICAL_REACTION', 'COMPARISON_SPLIT', 'PROCESS_TIMELINE', 'GEOMETRY_SPACE',
    'ILLUSTRATED_EXPLAINER'
));
```

### V4__render_resolution_1440p.sql

Nguồn: [V4__render_resolution_1440p.sql](../STEMotion_BE/src/main/resources/db/migration/V4__render_resolution_1440p.sql)

```sql
-- 1440p publishing preset. The render service keeps the 1920x1080 canvas and
-- scales the output, so every resolution shares one layout.

ALTER TABLE render_jobs DROP CONSTRAINT render_jobs_resolution_check;

ALTER TABLE render_jobs ADD CONSTRAINT render_jobs_resolution_check
    CHECK (resolution IN ('RES_720P', 'RES_1080P', 'RES_1440P', 'RES_4K'));
```

### V5__user_display_name.sql

Nguồn: [V5__user_display_name.sql](../STEMotion_BE/src/main/resources/db/migration/V5__user_display_name.sql)

```sql
-- Align the original varchar-based schema with the existing Supabase schema.
-- Keep the existing full_name column and synchronize display_name in the entity.
ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
UPDATE users SET display_name = full_name WHERE display_name IS NULL;
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
```

### V6__align_shared_scene_order.sql

Nguồn: [V6__align_shared_scene_order.sql](../STEMotion_BE/src/main/resources/db/migration/V6__align_shared_scene_order.sql)

```sql
-- Additive bridge for both the original Flyway schema and shared Supabase.
-- order_no is the API's canonical ordering. Preserve the older column for
-- consumers of the shared schema, without assigning every scene one default.
ALTER TABLE scenes ADD COLUMN IF NOT EXISTS order_index INTEGER;
UPDATE scenes SET order_index = order_no WHERE order_index IS DISTINCT FROM order_no;
ALTER TABLE scenes ALTER COLUMN order_index SET NOT NULL;
```

### V7__align_feedback_targets.sql

Nguồn: [V7__align_feedback_targets.sql](../STEMotion_BE/src/main/resources/db/migration/V7__align_feedback_targets.sql)

```sql
-- Preserve legacy clip/version targets while accepting the current project's
-- rendered-video target. No clips table row is invented for a render job.
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS target_type VARCHAR(30);
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS script_version_id VARCHAR(36);
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS scene_key VARCHAR(255);
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS timestamp_val DOUBLE PRECISION;
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS clip_id VARCHAR(36);

UPDATE feedback_comments f SET
    target_type = 'SCRIPT_INLINE', script_version_id = s.version_id, scene_key = s.id::text
FROM scenes s WHERE f.scene_id = s.id AND f.target_type IS NULL;
UPDATE feedback_comments SET target_type = 'CLIP_TIMESTAMP', timestamp_val = timestamp_sec
WHERE target_type IS NULL AND scene_id IS NULL AND timestamp_sec IS NOT NULL;

ALTER TABLE feedback_comments ALTER COLUMN target_type SET NOT NULL;
ALTER TABLE feedback_comments DROP CONSTRAINT IF EXISTS chk_feedback_target;
ALTER TABLE feedback_comments ADD CONSTRAINT chk_feedback_target CHECK (
    (target_type = 'SCRIPT_INLINE' AND script_version_id IS NOT NULL AND scene_key IS NOT NULL)
    OR (target_type = 'CLIP_TIMESTAMP' AND clip_id IS NOT NULL AND timestamp_val IS NOT NULL)
    OR (target_type = 'CLIP_TIMESTAMP' AND project_id IS NOT NULL AND timestamp_sec IS NOT NULL AND timestamp_sec >= 0)
) NOT VALID;
ALTER TABLE feedback_comments VALIDATE CONSTRAINT chk_feedback_target;
```

### V8__align_subject_and_user_status.sql

Nguồn: [V8__align_subject_and_user_status.sql](../STEMotion_BE/src/main/resources/db/migration/V8__align_subject_and_user_status.sql)

```sql
ALTER TABLE clip_projects DROP CONSTRAINT IF EXISTS clip_projects_subject_check;
ALTER TABLE clip_projects ADD CONSTRAINT clip_projects_subject_check CHECK (
    subject::text IN ('Math', 'Physics', 'Chemistry', 'Biology', 'CS', 'ComputerScience')
);
-- The original Flyway schema uses varchar with a CHECK; Supabase uses an enum.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status::text IN ('ACTIVE', 'INACTIVE', 'BANNED'));
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
               WHERE n.nspname=current_schema() AND t.typname='user_status') THEN
        EXECUTE format('ALTER TYPE %I.user_status ADD VALUE IF NOT EXISTS ''BANNED''', current_schema());
    END IF;
END $$;
```

### V9__workspace_invitation_tokens.sql

Nguồn: [V9__workspace_invitation_tokens.sql](../STEMotion_BE/src/main/resources/db/migration/V9__workspace_invitation_tokens.sql)

```sql
-- Legacy pending seats remain inactive until their administrator reissues a link.
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS invitation_token_hash VARCHAR(64);
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS invitation_expires_at TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_invitation_token
    ON workspace_members (invitation_token_hash) WHERE invitation_token_hash IS NOT NULL;
```

### V10__clip_artifacts.sql

Nguồn: [V10__clip_artifacts.sql](../STEMotion_BE/src/main/resources/db/migration/V10__clip_artifacts.sql)

```sql
-- Completed artifacts are distinct from mutable project/video workflow state.
-- The shared Supabase schema uses UUID keys; the legacy V1 baseline uses varchar.
DO $$
DECLARE job_id_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO job_id_type
      FROM pg_attribute WHERE attrelid = 'render_jobs'::regclass
      AND attname = 'id' AND NOT attisdropped;
    EXECUTE format('CREATE TABLE IF NOT EXISTS clips (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        render_job_id %s NOT NULL UNIQUE REFERENCES render_jobs(id),
        video_url text NOT NULL CHECK (length(btrim(video_url)) > 0),
        duration_sec integer CHECK (duration_sec >= 0),
        qa_status varchar(16) NOT NULL DEFAULT ''PENDING''
            CHECK (qa_status IN (''PENDING'', ''PASS'', ''FAIL'')),
        qa_report jsonb,
        created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
    )', job_id_type);
END $$;

-- The diagram-first Supabase baseline already has clips (native qa_result enum).
-- Retain that table, its constraints, IDs and enum; BaseEntity also needs this column.
ALTER TABLE clips ADD COLUMN IF NOT EXISTS updated_at timestamp(6);
UPDATE clips SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE clips ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE clips ALTER COLUMN updated_at SET NOT NULL;
-- The shared ERD schema requires duration_sec, but workers do not yet report a
-- measured duration. Unknown is NULL, never a fabricated zero.
ALTER TABLE clips ALTER COLUMN duration_sec DROP NOT NULL;
ALTER TABLE clips ALTER COLUMN qa_report DROP DEFAULT;

ALTER TABLE clips ADD CONSTRAINT clips_measured_duration_check
    CHECK (duration_sec IS NULL OR duration_sec >= 0);
ALTER TABLE clips ADD CONSTRAINT clips_artifact_url_check
    CHECK (length(btrim(video_url)) > 0);

-- Preserve existing job URLs; do not invent measured durations or QA results.
INSERT INTO clips (render_job_id, video_url, created_at, updated_at)
SELECT id, video_url, created_at, updated_at FROM render_jobs
WHERE status::text = 'COMPLETED' AND length(btrim(video_url)) > 0
ON CONFLICT (render_job_id) DO NOTHING;
```

### V11__explicit_review_decisions.sql

Nguồn: [V11__explicit_review_decisions.sql](../STEMotion_BE/src/main/resources/db/migration/V11__explicit_review_decisions.sql)

```sql
ALTER TABLE status_histories ADD COLUMN event_type varchar(32) NOT NULL DEFAULT 'PIPELINE_TRANSITION';
ALTER TABLE status_histories ADD COLUMN target_type varchar(16) NOT NULL DEFAULT 'PROJECT';
ALTER TABLE status_histories ADD COLUMN target_id varchar(36);
ALTER TABLE status_histories ADD COLUMN decision varchar(16);
ALTER TABLE status_histories ADD COLUMN reason text;
UPDATE status_histories SET target_id = project_id::text;
ALTER TABLE status_histories ALTER COLUMN target_id SET NOT NULL;
ALTER TABLE status_histories ADD CONSTRAINT status_history_event_shape CHECK (
    (event_type = 'PIPELINE_TRANSITION' AND target_type = 'PROJECT' AND decision IS NULL)
    OR (event_type = 'REVIEW_DECISION' AND target_type IN ('SCRIPT','CLIP')
        AND decision IS NOT NULL AND decision IN ('APPROVED','REJECTED')));
CREATE INDEX idx_status_history_target ON status_histories(target_type,target_id,created_at);

-- Polymorphic target_id is textual to support UUID and legacy varchar parents.
-- Bind to a real artifact/version in this project, never the mutable project alone.
CREATE FUNCTION stemotion_validate_history_target() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Compatibility during rollout: the old BE inserts project transitions
    -- without target_id. Fill it before the NOT NULL check, preserving its API.
    IF NEW.target_id IS NULL AND NEW.event_type='PIPELINE_TRANSITION' AND NEW.target_type='PROJECT' THEN
        NEW.target_id := NEW.project_id::text;
    END IF;
    IF NEW.target_type = 'PROJECT' AND NEW.target_id <> NEW.project_id::text THEN
        RAISE EXCEPTION 'History project target mismatch';
    ELSIF NEW.target_type = 'SCRIPT' AND NOT EXISTS (
        SELECT 1 FROM script_versions WHERE id::text = NEW.target_id
        AND project_id = NEW.project_id) THEN
        RAISE EXCEPTION 'Review script does not belong to project';
    ELSIF NEW.target_type = 'CLIP' AND NOT EXISTS (
        SELECT 1 FROM clips c JOIN render_jobs r ON r.id = c.render_job_id
        WHERE c.id::text = NEW.target_id AND r.project_id = NEW.project_id) THEN
        RAISE EXCEPTION 'Review Clip does not belong to project';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_history_target BEFORE INSERT ON status_histories
    FOR EACH ROW EXECUTE FUNCTION stemotion_validate_history_target();

CREATE FUNCTION stemotion_reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'status_histories is append-only';
END $$;
CREATE TRIGGER history_append_only BEFORE UPDATE OR DELETE ON status_histories
    FOR EACH ROW EXECUTE FUNCTION stemotion_reject_history_mutation();
CREATE TRIGGER history_no_truncate BEFORE TRUNCATE ON status_histories
    FOR EACH STATEMENT EXECUTE FUNCTION stemotion_reject_history_mutation();

CREATE FUNCTION stemotion_protect_review_target() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM status_histories WHERE target_type = TG_ARGV[0]
        AND target_id = OLD.id::text) THEN
        RAISE EXCEPTION 'Cannot delete an artifact/version referenced by review history';
    END IF;
    RETURN OLD;
END $$;
CREATE TRIGGER protect_reviewed_script BEFORE DELETE ON script_versions
    FOR EACH ROW EXECUTE FUNCTION stemotion_protect_review_target('SCRIPT');
CREATE TRIGGER protect_reviewed_clip BEFORE DELETE ON clips
    FOR EACH ROW EXECUTE FUNCTION stemotion_protect_review_target('CLIP');
```

### V12__ai_request_tracking.sql

Nguồn: [V12__ai_request_tracking.sql](../STEMotion_BE/src/main/resources/db/migration/V12__ai_request_tracking.sql)

```sql
DO $$
DECLARE user_type text; version_type text; scene_type text;
BEGIN
    SELECT format_type(atttypid,atttypmod) INTO user_type FROM pg_attribute
        WHERE attrelid='users'::regclass AND attname='id';
    SELECT format_type(atttypid,atttypmod) INTO version_type FROM pg_attribute
        WHERE attrelid='script_versions'::regclass AND attname='id';
    SELECT format_type(atttypid,atttypmod) INTO scene_type FROM pg_attribute
        WHERE attrelid='scenes'::regclass AND attname='id';
    EXECUTE format('CREATE TABLE IF NOT EXISTS ai_requests (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        requester_id %s REFERENCES users(id),
        request_type varchar(32) NOT NULL,
        script_version_id %s REFERENCES script_versions(id),
        scene_id %s REFERENCES scenes(id), clip_id uuid REFERENCES clips(id),
        model varchar(255) NOT NULL, response_data jsonb,
        decision varchar(16) DEFAULT ''PENDING'', cost_tokens integer,
        latency_ms integer, created_at timestamptz DEFAULT now()
    )',user_type,version_type,scene_type);
END $$;

ALTER TABLE ai_requests ADD COLUMN updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE ai_requests ADD COLUMN request_data jsonb;
ALTER TABLE ai_requests ADD COLUMN call_status varchar(16);
ALTER TABLE ai_requests ADD COLUMN input_tokens integer;
ALTER TABLE ai_requests ADD COLUMN output_tokens integer;
ALTER TABLE ai_requests ADD COLUMN monetary_cost numeric(20,10);
ALTER TABLE ai_requests ADD COLUMN cost_currency varchar(3);
UPDATE ai_requests SET call_status='UNKNOWN';
ALTER TABLE ai_requests ALTER COLUMN call_status SET DEFAULT 'PENDING';
ALTER TABLE ai_requests ALTER COLUMN call_status SET NOT NULL;
ALTER TABLE ai_requests ALTER COLUMN cost_tokens DROP DEFAULT;
ALTER TABLE ai_requests ALTER COLUMN latency_ms DROP DEFAULT;
-- Generation is called before a draft exists; failures also need a durable row.
-- Optional artifact FKs in the diagram may therefore all be NULL.
ALTER TABLE ai_requests DROP CONSTRAINT IF EXISTS chk_ai_target;
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_single_target CHECK (
    num_nonnulls(script_version_id,scene_id,clip_id) <= 1);
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_call_status CHECK (
    call_status IN ('UNKNOWN','PENDING','SUCCEEDED','FAILED'));
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_nonnegative_usage CHECK (
    (cost_tokens IS NULL OR cost_tokens >= 0) AND
    (input_tokens IS NULL OR input_tokens >= 0) AND
    (output_tokens IS NULL OR output_tokens >= 0) AND
    (latency_ms IS NULL OR latency_ms >= 0) AND
    (monetary_cost IS NULL OR monetary_cost >= 0));
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_cost_currency CHECK (
    (monetary_cost IS NULL AND cost_currency IS NULL) OR
    (monetary_cost IS NOT NULL AND cost_currency IS NOT NULL));
CREATE INDEX idx_ai_request_requester_created ON ai_requests(requester_id,created_at);
```

### V13__publication_transition.sql

Nguồn: [V13__publication_transition.sql](../STEMotion_BE/src/main/resources/db/migration/V13__publication_transition.sql)

```sql
DO $$
DECLARE project_type text; user_type text;
BEGIN
    SELECT format_type(atttypid,atttypmod) INTO project_type FROM pg_attribute
        WHERE attrelid='clip_projects'::regclass AND attname='id';
    SELECT format_type(atttypid,atttypmod) INTO user_type FROM pg_attribute
        WHERE attrelid='users'::regclass AND attname='id';
    EXECUTE format('CREATE TABLE IF NOT EXISTS publications (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        clip_id uuid REFERENCES clips(id), publisher_id %s REFERENCES users(id),
        platform varchar(32) NOT NULL CHECK (platform IN (''YOUTUBE'',''INTERNAL_LIBRARY'')),
        metadata jsonb, published_at timestamptz DEFAULT now()
    )',user_type);
    EXECUTE format('ALTER TABLE publications ADD COLUMN project_id %s REFERENCES clip_projects(id)',project_type);
END $$;
ALTER TABLE publications ADD COLUMN created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE publications ADD COLUMN updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE publications p SET project_id=r.project_id
    FROM clips c JOIN render_jobs r ON r.id=c.render_job_id WHERE p.clip_id=c.id;
-- No guess for an orphan publication, and no duplicate cleanup by arbitrary deletion.
ALTER TABLE publications ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE publications ADD CONSTRAINT publications_project_platform_key UNIQUE(project_id,platform);
-- Keep publish_records for the API adapter. No automatic YouTube backfill:
-- a legacy record or URL is not proof of a real uploaded/approved publication.
```

### V14__director_template_metadata.sql

Nguồn: [V14__director_template_metadata.sql](../STEMotion_BE/src/main/resources/db/migration/V14__director_template_metadata.sql)

```sql
-- Additive metadata only. Existing scene/template API fields remain unchanged.
ALTER TABLE remotion_templates ADD COLUMN subject VARCHAR(32);
ALTER TABLE remotion_templates ADD COLUMN asset_pack VARCHAR(128);
ALTER TABLE remotion_templates ADD COLUMN slots JSONB;
ALTER TABLE remotion_templates ADD CONSTRAINT ck_template_slots_object
    CHECK (slots IS NULL OR jsonb_typeof(slots) = 'object');
```

### V15__composed_scene.sql

Nguồn: [V15__composed_scene.sql](../STEMotion_BE/src/main/resources/db/migration/V15__composed_scene.sql)

```sql
-- Composed scenes: assembled from blocks instead of a fixed template. Their
-- frame and blocks live in the existing JSONB props, so only the scene type
-- needs to widen.
--
-- The Flyway baseline stores scene_type as varchar with a CHECK constraint;
-- the diagram-first Supabase baseline uses an enum type. Handle both.
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'scenes'::regclass AND attname = 'scene_type';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE scenes DROP CONSTRAINT IF EXISTS scenes_scene_type_check;
        ALTER TABLE scenes ADD CONSTRAINT scenes_scene_type_check CHECK (scene_type IN (
            'TITLE_HERO', 'MATH_FORMULA', 'DIAGRAM_EXPLAINER', 'DATA_CHART',
            'ALGORITHM_WALKTHROUGH', 'STEM_QUIZ', 'OUTRO',
            'CHEMICAL_REACTION', 'COMPARISON_SPLIT', 'PROCESS_TIMELINE', 'GEOMETRY_SPACE',
            'ILLUSTRATED_EXPLAINER', 'COMPOSED'
        ));
    ELSE
        EXECUTE format('ALTER TYPE %s ADD VALUE IF NOT EXISTS %L', col_type, 'COMPOSED');
    END IF;
END $$;
```

### V16__member_status_inactive_value.sql

Nguồn: [V16__member_status_inactive_value.sql](../STEMotion_BE/src/main/resources/db/migration/V16__member_status_inactive_value.sql)

```sql
-- Adds INACTIVE to the workspace member status enum on databases that store
-- status as an enum (the diagram-first Supabase baseline).
--
-- PostgreSQL refuses to use an enum value in the same transaction that added
-- it, and Flyway runs each migration in its own transaction. V17 moves pending
-- seats to INACTIVE, so the value is added here, one migration earlier.
-- On the Flyway baseline status is varchar with a CHECK, and this is a no-op.
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'status';
    IF NOT (col_type LIKE 'character varying%' OR col_type = 'text') THEN
        EXECUTE format('ALTER TYPE %s ADD VALUE IF NOT EXISTS %L', col_type, 'INACTIVE');
    END IF;
END $$;
```

### V17__admin_provisioned_membership.sql

Nguồn: [V17__admin_provisioned_membership.sql](../STEMotion_BE/src/main/resources/db/migration/V17__admin_provisioned_membership.sql)

```sql
-- Migration V17: Admin-provisioned workspace membership and first-login password change.
--
-- 1. Data migration for legacy invitation seats:
--    - Rows in workspace_members with user_id IS NULL (pending email invitations that were never accepted)
--      are deleted because they cannot be associated with any user account.
--    - Rows with status 'INVITED' that already have an associated user_id are converted to 'INACTIVE'.
--    - Legacy members with role 'ADMIN' inside the workspace are converted to is_leader = true, role = 'WRITER'.
--
-- 2. Schema changes:
--    - workspace_members drops invited_email, invitation_token_hash, invitation_expires_at.
--    - workspace_members.user_id is set to NOT NULL.
--    - uk_workspace_member_email is dropped, replaced by uk_workspace_member_user UNIQUE (workspace_id, user_id).
--    - workspace_members.status constraint/enum allows only 'ACTIVE', 'INACTIVE'.
--    - workspace_members.is_leader BOOLEAN NOT NULL DEFAULT false is added.
--    - users.must_change_password BOOLEAN NOT NULL DEFAULT false is added.

-- Step 1: On the varchar baseline drop the status check so 'INACTIVE' is allowed during the backfill.
-- (On the enum baseline V16 already added INACTIVE, in its own transaction.)
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'status';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_status_check;
    END IF;
END $$;

-- Step 2: Clean up and backfill legacy data
-- WARNING: Unclaimed invitations without an associated user_id cannot be mapped to any account and will be deleted.
DELETE FROM workspace_members WHERE user_id IS NULL;

-- Migrate any invited seats that had a user_id linked to INACTIVE
UPDATE workspace_members SET status = 'INACTIVE' WHERE status = 'INVITED' AND user_id IS NOT NULL;

-- Step 3: Add is_leader column
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS is_leader BOOLEAN NOT NULL DEFAULT false;

-- Migrate existing workspace ADMIN roles to is_leader = true and role = 'WRITER'
UPDATE workspace_members SET is_leader = true, role = 'WRITER' WHERE role = 'ADMIN';

-- Step 4: Drop invitation columns and related index
DROP INDEX IF EXISTS uk_workspace_invitation_token;
ALTER TABLE workspace_members DROP COLUMN IF EXISTS invited_email;
ALTER TABLE workspace_members DROP COLUMN IF EXISTS invitation_token_hash;
ALTER TABLE workspace_members DROP COLUMN IF EXISTS invitation_expires_at;

-- Step 5: user_id NOT NULL and unique constraint on (workspace_id, user_id)
ALTER TABLE workspace_members ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS uk_workspace_member_email;
ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS uk_workspace_member_user;
ALTER TABLE workspace_members ADD CONSTRAINT uk_workspace_member_user UNIQUE (workspace_id, user_id);

-- Step 6: Enforce final status constraint (only 'ACTIVE', 'INACTIVE')
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'status';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_status_check;
        ALTER TABLE workspace_members ADD CONSTRAINT workspace_members_status_check CHECK (status::text IN ('ACTIVE', 'INACTIVE'));
    END IF;
END $$;

-- Align workspace member role constraint (workspace roles are strictly WRITER, REVIEWER, PRODUCER)
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'role';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_role_check;
        ALTER TABLE workspace_members ADD CONSTRAINT workspace_members_role_check CHECK (role::text IN ('WRITER', 'REVIEWER', 'PRODUCER'));
    END IF;
END $$;

-- Step 7: Add must_change_password flag to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;
```

### V18__ai_pricing_provenance.sql

Nguồn: [V18__ai_pricing_provenance.sql](../STEMotion_BE/src/main/resources/db/migration/V18__ai_pricing_provenance.sql)

```sql
-- Additive only. Existing model/input_tokens/output_tokens/monetary_cost columns are reused.
-- Do not rewrite V12 or reprice old requests. Production apply requires separate approval/backup.
ALTER TABLE ai_requests ADD COLUMN pricing_version VARCHAR(128);
ALTER TABLE ai_requests ADD COLUMN pricing_input_per_million NUMERIC(20,10);
ALTER TABLE ai_requests ADD COLUMN pricing_output_per_million NUMERIC(20,10);
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_pricing_provenance CHECK (
    (pricing_version IS NULL AND pricing_input_per_million IS NULL AND pricing_output_per_million IS NULL)
    OR (pricing_version IS NOT NULL AND pricing_input_per_million >= 0 AND pricing_output_per_million >= 0
        AND pricing_input_per_million IS NOT NULL AND pricing_output_per_million IS NOT NULL));
CREATE INDEX idx_ai_usage_created_model_user ON ai_requests(created_at, model, requester_id);
```

### V19__production_composition.sql

Nguồn: [V19__production_composition.sql](../STEMotion_BE/src/main/resources/db/migration/V19__production_composition.sql)

```sql
-- Production composition (proposal, Production Workspace; Conceptual ERD:
-- Producer edits Composition, ScriptVersion mapped to Composition, Composition
-- contains Scene, Composition triggers RenderJob): the producer opens an
-- approved script as an auto-generated draft and edits each scene's properties
-- there. The approved script version itself stays frozen.
--
-- One composition per approved script version. Every save increments
-- revision, and a render job keeps the exact props it was sent, so a render is
-- reproducible per composition version (proposal, reliability).
--
-- The shared Supabase baseline already has compositions (script_version_id,
-- producer_id, resolution, fps) and render_jobs.composition_id; they are kept
-- and extended. The Flyway baseline (varchar(36) keys) gets the same table.
DO $$
DECLARE project_id_type text; version_id_type text; user_id_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO project_id_type FROM pg_attribute
        WHERE attrelid = 'clip_projects'::regclass AND attname = 'id' AND NOT attisdropped;
    SELECT format_type(atttypid, atttypmod) INTO version_id_type FROM pg_attribute
        WHERE attrelid = 'script_versions'::regclass AND attname = 'id' AND NOT attisdropped;
    SELECT format_type(atttypid, atttypmod) INTO user_id_type FROM pg_attribute
        WHERE attrelid = 'users'::regclass AND attname = 'id' AND NOT attisdropped;
    EXECUTE format('CREATE TABLE IF NOT EXISTS compositions (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        script_version_id %s UNIQUE REFERENCES script_versions(id) ON DELETE CASCADE,
        producer_id %s REFERENCES users(id) ON DELETE SET NULL,
        resolution varchar(50) DEFAULT ''1080p'',
        fps integer DEFAULT 30,
        created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
    )', version_id_type, user_id_type);
    EXECUTE format('ALTER TABLE compositions ADD COLUMN IF NOT EXISTS project_id %s
        REFERENCES clip_projects(id) ON DELETE CASCADE', project_id_type);
END $$;

ALTER TABLE compositions ADD COLUMN IF NOT EXISTS revision integer;
ALTER TABLE compositions ADD COLUMN IF NOT EXISTS scenes jsonb;

-- Rows from before this migration (shared baseline): the project comes from the
-- script version, the scenes from that version's scene rows, as revision 1.
UPDATE compositions c SET project_id = v.project_id
    FROM script_versions v WHERE v.id = c.script_version_id AND c.project_id IS NULL;
UPDATE compositions c SET scenes = coalesce((
        SELECT jsonb_agg(coalesce(s.props, '{}'::jsonb) || jsonb_build_object(
                   'id', s.id::text, 'type', s.scene_type::text, 'title', s.title,
                   'narration', s.narration, 'durationInFrames', s.duration_in_frames)
               ORDER BY s.order_no)
        FROM scenes s WHERE s.version_id = c.script_version_id), '[]'::jsonb)
    WHERE c.scenes IS NULL;
UPDATE compositions SET revision = 1 WHERE revision IS NULL;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM compositions WHERE script_version_id IS NULL OR project_id IS NULL) THEN
        RAISE EXCEPTION 'compositions rows without a script version or project: link or remove them, then rerun V19';
    END IF;
END $$;

ALTER TABLE compositions ALTER COLUMN script_version_id SET NOT NULL;
ALTER TABLE compositions ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE compositions ALTER COLUMN revision SET NOT NULL;
ALTER TABLE compositions ALTER COLUMN scenes SET NOT NULL;
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_revision_check;
ALTER TABLE compositions ADD CONSTRAINT compositions_revision_check CHECK (revision >= 1);
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_scenes_check;
ALTER TABLE compositions ADD CONSTRAINT compositions_scenes_check CHECK (jsonb_typeof(scenes) = 'array');
CREATE INDEX IF NOT EXISTS idx_compositions_project ON compositions (project_id);

-- Additive only: existing jobs keep NULL (rendered straight from the script).
-- The shared baseline already has composition_id; it is kept as it is.
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS composition_id uuid
    REFERENCES compositions(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_render_jobs_composition ON render_jobs (composition_id);
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS composition_revision integer;
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS input_props jsonb;
ALTER TABLE render_jobs DROP CONSTRAINT IF EXISTS render_jobs_composition_revision_check;
ALTER TABLE render_jobs ADD CONSTRAINT render_jobs_composition_revision_check
    CHECK (composition_revision IS NULL OR composition_revision >= 1);
```

## 2. Entity mappings (15 @Entity + BaseEntity)

### BaseEntity.java

Nguồn: [BaseEntity.java](../STEMotion_BE/src/main/java/com/stemotion/core/entity/BaseEntity.java)

```java
package com.stemotion.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private String id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### AiRequest.java

Nguồn: [AiRequest.java](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/entity/AiRequest.java)

```java
package com.stemotion.domain.ai.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.production.entity.Clip;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Entity @Table(name="ai_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiRequest extends BaseEntity {
    public enum RequestType { ANALYSIS, SEGMENTATION, TTS, CAPTION, QA }
    public enum Decision { PENDING, ACCEPTED, IGNORED }
    public enum CallStatus { UNKNOWN, PENDING, SUCCEEDED, FAILED }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="requester_id")
    private User requester;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="script_version_id")
    private ScriptVersion scriptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="scene_id")
    private Scene scene;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="clip_id")
    private Clip clip;
    @Enumerated(EnumType.STRING) @Column(name="request_type",nullable=false,length=32)
    private RequestType requestType;
    @Column(name="model",nullable=false)
    private String model;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="request_data",columnDefinition="jsonb")
    private String requestData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_data",columnDefinition="jsonb")
    private String responseData;
    @Enumerated(EnumType.STRING) @Column(name="decision",length=16)
    @Builder.Default private Decision decision=Decision.PENDING;
    @Enumerated(EnumType.STRING) @Column(name="call_status",nullable=false,length=16)
    @Builder.Default private CallStatus callStatus=CallStatus.PENDING;
    @Column(name="cost_tokens") private Integer costTokens;
    @Column(name="input_tokens") private Integer inputTokens;
    @Column(name="output_tokens") private Integer outputTokens;
    @Column(name="latency_ms") private Integer latencyMs;
    @Column(name="monetary_cost",precision=20,scale=10) private BigDecimal monetaryCost;
    @Column(name="cost_currency",length=3) private String costCurrency;
    @Column(name="pricing_version",length=128) private String pricingVersion;
    @Column(name="pricing_input_per_million",precision=20,scale=10) private BigDecimal pricingInputPerMillion;
    @Column(name="pricing_output_per_million",precision=20,scale=10) private BigDecimal pricingOutputPerMillion;
}
```

### User.java

Nguồn: [User.java](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/entity/User.java)

```java
package com.stemotion.domain.auth.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    // The shared Supabase schema requires both name columns.
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @PrePersist
    @PreUpdate
    private void synchronizeDisplayName() {
        displayName = fullName;
    }

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "google_id", unique = true)
    private String googleId;

    // Null for accounts created through Google sign-in
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private UserRole role = UserRole.WRITER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    public enum UserRole {
        WRITER,
        REVIEWER,
        PRODUCER,
        ADMIN
    }

    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        BANNED
    }
}
```

### Clip.java

Nguồn: [Clip.java](../STEMotion_BE/src/main/java/com/stemotion/domain/production/entity/Clip.java)

```java
package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A completed render artifact. Human review is tracked separately in history. */
@Entity
@Table(name = "clips")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Clip extends BaseEntity {
    public enum QaStatus { PENDING, PASS, FAIL }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "render_job_id", nullable = false, unique = true)
    private RenderJob renderJob;

    @Column(name = "video_url", nullable = false, columnDefinition = "text")
    private String videoUrl;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "qa_status", nullable = false, length = 16)
    @Builder.Default
    private QaStatus qaStatus = QaStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "qa_report", columnDefinition = "jsonb")
    private String qaReport;
}
```

### Composition.java

Nguồn: [Composition.java](../STEMotion_BE/src/main/java/com/stemotion/domain/production/entity/Composition.java)

```java
package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.entity.ScriptVersion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The producer's working copy of an approved script (proposal, Production
 * Workspace): the same ordered scenes and templates, with the per-scene
 * properties the producer has tuned. The approved script version is never
 * edited; a render takes this copy when it exists.
 */
@Entity
@Table(name = "compositions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Composition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_version_id", nullable = false, unique = true)
    private ScriptVersion scriptVersion;

    /** Composition version: 1 on the first save, +1 on every later save. */
    @Column(name = "revision", nullable = false)
    private Integer revision;

    /** Scenes in the SceneData shape, durations in the project's fps. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenes", nullable = false, columnDefinition = "jsonb")
    private String scenes;

    /** The producer who saved the latest revision (ERD: Producer edits Composition). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producer_id")
    private User producer;
}
```

### Publication.java

Nguồn: [Publication.java](../STEMotion_BE/src/main/java/com/stemotion/domain/production/entity/Publication.java)

```java
package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity @Table(name = "publications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Publication extends BaseEntity {
    public enum Platform { YOUTUBE, INTERNAL_LIBRARY }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false)
    private ClipProject project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="clip_id")
    private Clip clip;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="publisher_id")
    private User publisher;
    @Enumerated(EnumType.STRING) @Column(name="platform",nullable=false,length=32)
    private Platform platform;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="metadata",columnDefinition="jsonb")
    private String metadata;
    @Column(name="published_at")
    private LocalDateTime publishedAt;
}
```

### PublishRecord.java

Nguồn: [PublishRecord.java](../STEMotion_BE/src/main/java/com/stemotion/domain/production/entity/PublishRecord.java)

```java
package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Where and when an approved clip was published.
 */
@Entity
@Table(name = "publish_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by", nullable = false)
    private User publishedBy;

    /** Comma-separated PublishTarget names, e.g. "LMS,YOUTUBE". */
    @Column(name = "targets", nullable = false)
    private String targets;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "lms_embed_code", columnDefinition = "TEXT")
    private String lmsEmbedCode;

    @Column(name = "youtube_url")
    private String youtubeUrl;
}
```

### RemotionTemplate.java

Nguồn: [RemotionTemplate.java](../STEMotion_BE/src/main/java/com/stemotion/domain/production/entity/RemotionTemplate.java)

```java
package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "remotion_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemotionTemplate extends BaseEntity {

    @Column(name = "subject")
    private String subject;

    @Column(name = "asset_pack")
    private String assetPack;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "slots", columnDefinition = "jsonb")
    private String slots;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "props_schema", columnDefinition = "TEXT")
    private String propsSchema;

    @Column(name = "preview_url")
    private String previewUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
```

### RenderJob.java

Nguồn: [RenderJob.java](../STEMotion_BE/src/main/java/com/stemotion/domain/production/entity/RenderJob.java)

```java
package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.RenderResolution;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.entity.ScriptVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tracks one Remotion render handled by the external Node worker pool.
 *
 * <p>This row is the API's own record of the job; {@code externalJobId} is the
 * BullMQ id returned by the render service, which owns the actual execution.
 */
@Entity
@Table(name = "render_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenderJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private ScriptVersion scriptVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "external_job_id")
    private String externalJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RenderStatus status = RenderStatus.QUEUED;

    @Column(name = "progress", nullable = false)
    @Builder.Default
    private Integer progress = 0;

    @Column(name = "stage")
    private String stage;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", nullable = false)
    @Builder.Default
    private RenderResolution resolution = RenderResolution.RES_1080P;

    @Column(name = "fps", nullable = false)
    @Builder.Default
    private Integer fps = 30;

    @Column(name = "tts_voice")
    private String ttsVoice;

    /** Retry counter; the proposal allows three attempts before alerting. */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** The composition that triggered this render (ERD); null when rendered straight from the script. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composition_id")
    private Composition composition;

    /** Composition version rendered; null when rendered straight from the approved script. */
    @Column(name = "composition_revision")
    private Integer compositionRevision;

    /** Exact props sent to the worker, so a retry renders the same composition version. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_props", columnDefinition = "jsonb")
    private String inputProps;
}
```

### ClipProject.java

Nguồn: [ClipProject.java](../STEMotion_BE/src/main/java/com/stemotion/domain/project/entity/ClipProject.java)

```java
package com.stemotion.domain.project.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.workspace.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A STEM clip project.
 *
 * <p>Carries the two independent states the front end reads: {@code scriptStatus}
 * for the editorial side and {@code videoStatus} for the rendered clip. The
 * single pipeline state from the proposal (Draft → Script Review → Approved →
 * In Production → Clip Review → Published) is derived from that pair by
 * {@link #getPipelineStatus()} rather than stored separately, so the board and
 * the API can never disagree about where a project stands.
 */
@Entity
@Table(name = "clip_projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClipProject extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @jakarta.persistence.Convert(converter = com.stemotion.core.enums.StemSubjectConverter.class)
    @Column(name = "subject", nullable = false)
    private StemSubject subject;

    @Column(name = "grade_level")
    private String gradeLevel;

    @Column(name = "fps", nullable = false)
    @Builder.Default
    private Integer fps = 30;

    /** Sum of the scene durations of the current script version, in seconds. */
    @Column(name = "total_duration_seconds", nullable = false)
    @Builder.Default
    private Integer totalDurationSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_status", nullable = false)
    @Builder.Default
    private ScriptStatus scriptStatus = ScriptStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", nullable = false)
    @Builder.Default
    private VideoStatus videoStatus = VideoStatus.NOT_RENDERED;

    /** URL of the most recently rendered MP4, if any. */
    @Column(name = "video_url")
    private String videoUrl;

    /** Set once the project is retired; keeps it out of the active boards. */
    @Column(name = "archived", nullable = false)
    @Builder.Default
    private Boolean archived = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    /**
     * Collapses the editorial and clip states into the single lifecycle state
     * used by the pipeline board and the status history.
     */
    @Transient
    public ProjectStatus getPipelineStatus() {
        if (Boolean.TRUE.equals(archived)) {
            return ProjectStatus.ARCHIVED;
        }
        return switch (scriptStatus) {
            case DRAFT, CHANGE_REQUESTED -> ProjectStatus.DRAFT;
            case IN_REVIEW -> ProjectStatus.SCRIPT_REVIEW;
            case APPROVED -> switch (videoStatus) {
                case NOT_RENDERED -> ProjectStatus.APPROVED;
                case RENDERING -> ProjectStatus.IN_PRODUCTION;
                case IN_QA -> ProjectStatus.CLIP_REVIEW;
                case APPROVED -> ProjectStatus.CLIP_REVIEW;
                case PUBLISHED -> ProjectStatus.PUBLISHED;
            };
        };
    }
}
```

### StatusHistory.java

Nguồn: [StatusHistory.java](../STEMotion_BE/src/main/java/com/stemotion/domain/project/entity/StatusHistory.java)

```java
package com.stemotion.domain.project.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "status_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusHistory extends BaseEntity {

    public enum EventType { PIPELINE_TRANSITION, REVIEW_DECISION }
    public enum TargetType { PROJECT, SCRIPT, CLIP }
    public enum Decision { APPROVED, REJECTED }

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 16)
    private Decision decision;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ProjectStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ProjectStatus toStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;
}
```

### FeedbackComment.java

Nguồn: [FeedbackComment.java](../STEMotion_BE/src/main/java/com/stemotion/domain/review/entity/FeedbackComment.java)

```java
package com.stemotion.domain.review.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.entity.Scene;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A review note. Covers both review passes described in the proposal:
 * inline per-scene comments on the script, and timestamped pins on the
 * rendered clip (where {@code timestampSec} points into the video).
 */
@Entity
@Table(name = "feedback_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackComment extends BaseEntity {

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "script_version_id", columnDefinition = "uuid")
    private String scriptVersionId;

    @Column(name = "scene_key")
    private String sceneKey;

    @Column(name = "timestamp_val")
    private Double timestampValue;

    @PrePersist
    @PreUpdate
    void synchronizeTarget() {
        targetType = scene == null ? "CLIP_TIMESTAMP" : "SCRIPT_INLINE";
        scriptVersionId = scene == null ? null : scene.getScriptVersion().getId();
        sceneKey = scene == null ? null : scene.getId();
        timestampValue = timestampSec;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    /** Set for an inline script comment; null for a clip-level pin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private Scene scene;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Position in the rendered clip, in seconds (e.g. 15.5). */
    @Column(name = "timestamp_sec")
    private Double timestampSec;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Free-form label used by the reviewer UI, e.g. "Đồ họa". */
    @Column(name = "tag")
    private String tag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FeedbackStatus status = FeedbackStatus.OPEN;
}
```

### Scene.java

Nguồn: [Scene.java](../STEMotion_BE/src/main/java/com/stemotion/domain/script/entity/Scene.java)

```java
package com.stemotion.domain.script.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.SceneType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One scene of a script version, mapped to a Remotion template.
 *
 * <p>The seven scene types each carry different template props (latex steps,
 * chart data points, code walk-through steps, quiz options...). Rather than a
 * column per variant, the type-specific half of the payload is stored as JSONB
 * in {@code props} and validated against the scene type in the service layer.
 */
@Entity
@Table(name = "scenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scene extends BaseEntity {

    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    /** Transitional Supabase column; kept equal to the API's canonical order. */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @PrePersist
    @PreUpdate
    void synchronizeOrder() {
        orderIndex = orderNo;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "scene_type", nullable = false)
    private SceneType sceneType;

    @Column(name = "title", nullable = false)
    private String title;

    /** Narration read by the TTS voice. */
    @Column(name = "narration", columnDefinition = "TEXT")
    private String narration;

    /** Scene length in frames; seconds = durationInFrames / project fps. */
    @Column(name = "duration_in_frames", nullable = false)
    @Builder.Default
    private Integer durationInFrames = 90;

    /** Template-specific props as JSON, shaped by {@link #sceneType}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "props", columnDefinition = "jsonb")
    private String props;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private ScriptVersion scriptVersion;
}
```

### ScriptVersion.java

Nguồn: [ScriptVersion.java](../STEMotion_BE/src/main/java/com/stemotion/domain/script/entity/ScriptVersion.java)

```java
package com.stemotion.domain.script.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An immutable-by-convention snapshot of a project's script. A new version is
 * created on every resubmission so reviewers can diff iterations.
 *
 * <p>Uses the same {@link ScriptStatus} vocabulary as the project, which is the
 * spelling the front end's STEMScript contract expects.
 */
@Entity
@Table(name = "script_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptVersion extends BaseEntity {

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ScriptStatus status = ScriptStatus.DRAFT;

    /** Reviewer note attached when the version is returned for changes. */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    /** Marks the version the producer renders from. */
    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
```

### Workspace.java

Nguồn: [Workspace.java](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/entity/Workspace.java)

```java
package com.stemotion.domain.workspace.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A subject department ("tổ bộ môn") that owns clip projects and members.
 */
@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "department")
    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
```

### WorkspaceMember.java

Nguồn: [WorkspaceMember.java](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/entity/WorkspaceMember.java)

```java
package com.stemotion.domain.workspace.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Links an active user to a workspace with the role they play inside it.
 *
 * <p>Members are provisioned directly by administrators or workspace leaders.
 * A member can optionally be designated as a workspace leader ({@code isLeader}).
 */
@Entity
@Table(
    name = "workspace_members",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_workspace_member_user",
        columnNames = {"workspace_id", "user_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private User.UserRole role;

    @Column(name = "is_leader", nullable = false)
    @Builder.Default
    private boolean isLeader = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;
}
```

## 3. DTO fields / validation / response models

### AdminDtos.java

Nguồn: [AdminDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/admin/dto/AdminDtos.java)

```java
package com.stemotion.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AdminDtos {

    private AdminDtos() {}

    /**
     * Mirrors the SystemMetrics interface consumed by the admin dashboard.
     * Percentages are 0-100; storage is in gigabytes.
     */
    public record SystemMetrics(
        double cpuUsage,
        double gpuUsage,
        double memoryUsage,
        long activeRenderJobs,
        long completedToday,
        double storageUsedGb,
        long failedToday,
        double successRate
    ) {}

    public record AdminRenderJobSummary(
        String id,
        String externalJobId,
        String projectId,
        String projectTitle,
        String subject,
        String requestedByName,
        String status,
        int progress,
        String stage,
        String resolution,
        int fps,
        String videoUrl,
        String errorMessage,
        int attemptCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    /** Nullable usage fields mean unavailable until real request aggregation is wired. */
    public record AiUsageStats(
        long totalProjects,
        long totalScriptVersions,
        long totalScenesGenerated,
        Long estimatedAiCalls,
        Long avgLatencyMs,
        Long estimatedTokensUsed,
        Double estimatedCostUsd,
        Double monthlyBudgetUsd,
        String modelName,
        String ttsProvider
    ) {}

    public record AdminDashboardOverview(
        SystemMetrics metrics,
        AiUsageStats aiUsage,
        List<AdminRenderJobSummary> recentRenderJobs,
        Map<String, Long> projectsBySubject
    ) {}
}
```

### AiDtos.java

Nguồn: [AiDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/dto/AiDtos.java)

```java
package com.stemotion.domain.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.enums.StemEnums.StemSubject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiDtos {

    private AiDtos() {}

    /** The on-demand analyses offered in the Writer studio. */
    public enum AiAction {
        /** Re-cut the script into scenes with suggested durations and templates. */
        resegment,
        /** Score readability against the target grade. */
        grade,
        /** Pull out the STEM concepts covered. */
        extract,
        /** Flag inconsistent or wrong scientific terminology. */
        terms,
        /**
         * Propose three visual metaphors for one scene, each already staged as an
         * ILLUSTRATED_EXPLAINER scene. The writer picks one or none.
         */
        metaphor
    }

    public record GenerateScriptRequest(
        @NotBlank(message = "topic is required")
        String topic,

        @NotNull(message = "subject is required")
        StemSubject subject,

        @NotBlank(message = "gradeLevel is required")
        String gradeLevel,

        Integer targetDurationSec,

        @Size(max = 500, message = "narrativeBrief must be at most 500 characters")
        String narrativeBrief,
        /**
         * How the video should lead the viewer through the lesson, in the
         * writer's own words or copied from a suggested approach. Empty lets
         * the AI choose.
         */
        @Size(max = 800, message = "approach must be at most 800 characters")
        String approach
    ) {
        public GenerateScriptRequest(String topic, StemSubject subject, String gradeLevel, Integer targetDurationSec) {
            this(topic, subject, gradeLevel, targetDurationSec, null, null);
        }
    }

    /** Asks for several different ways to lead a video through one lesson. */
    public record ApproachRequest(
        @NotBlank(message = "topic is required")
        String topic,

        @NotNull(message = "subject is required")
        StemSubject subject,

        @NotBlank(message = "gradeLevel is required")
        String gradeLevel
    ) {}

    public record ScriptActionRequest(
        @NotNull(message = "action is required")
        AiAction action,

        @NotBlank(message = "scriptText is required")
        String scriptText,

        StemSubject subject,

        String gradeLevel
    ) {}

    /**
     * Raw model output plus the metadata the UI shows next to each suggestion.
     * The writer accepts or ignores it, so nothing is applied automatically.
     */
    public record AiActionResponse(
        AiAction action,
        JsonNode result,
        String model,
        long elapsedMs,
        String requestId
    ) {
        public AiActionResponse(AiAction action,JsonNode result,String model,long elapsedMs) {
            this(action,result,model,elapsedMs,null);
        }
    }

    public record AiDecisionRequest(@NotNull com.stemotion.domain.ai.entity.AiRequest.Decision decision) {}

    /** Suggestions returned by {@code resegment}, ready to become scenes. */
    public record SuggestedScene(
        String type,
        String title,
        String narration,
        Integer suggestedDurationSec,
        List<String> concepts,
        List<String> suggestedIcons
    ) {}
}
```

### AuthDtos.java

Nguồn: [AuthDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/dto/AuthDtos.java)

```java
package com.stemotion.domain.auth.dto;

import com.stemotion.domain.auth.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "fullName is required")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        User.UserRole role
    ) {}

    public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        String password
    ) {}

    public record GoogleLoginRequest(
        @NotBlank(message = "credential is required")
        String credential
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, message = "newPassword must be at least 8 characters")
        String newPassword
    ) {}

    public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserResponse user
    ) {}

    public record UserResponse(
        String id,
        String fullName,
        String email,
        User.UserRole role,
        User.UserStatus status,
        boolean mustChangePassword,
        LocalDateTime createdAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.isMustChangePassword(),
                user.getCreatedAt()
            );
        }
    }

    public record UpdateUserRequest(
        String fullName,
        User.UserRole role,
        User.UserStatus status,

        /** A new temporary password; the user must change it at the next sign-in. */
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
    ) {}
}
```

### LibraryDtos.java

Nguồn: [LibraryDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/library/dto/LibraryDtos.java)

```java
package com.stemotion.domain.library.dto;

import com.stemotion.core.enums.StemEnums.StemSubject;

import java.time.LocalDateTime;

public final class LibraryDtos {

    private LibraryDtos() {}

    /** A published clip as the internal library lists it. */
    public record LibraryItem(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        Integer sceneCount,
        String ownerName,
        String workspaceName,
        /** Direct MP4 link for download. */
        String videoUrl,
        /** Ready-to-paste iframe for Canvas/Moodle. */
        String embedCode,
        String youtubeUrl,
        LocalDateTime publishedAt
    ) {}
}
```

### ProductionDtos.java

Nguồn: [ProductionDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/production/dto/ProductionDtos.java)

```java
package com.stemotion.domain.production.dto;

import com.stemotion.core.enums.StemEnums.PublishTarget;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.production.entity.PublishRecord;
import com.stemotion.domain.production.entity.RemotionTemplate;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProductionDtos {

    private ProductionDtos() {}

    // ----------------------------------------------------------------- render

    public record StartRenderRequest(
        String resolution,
        Integer fps,
        String ttsVoice
    ) {}

    public record RenderJobResponse(
        String jobId,
        String projectId,
        RenderStatus status,
        Integer progress,
        String stage,
        String videoUrl,
        String errorMessage,
        Integer estimatedSeconds,
        LocalDateTime createdAt,
        /** Script version the clip was rendered from, so re-renders can be compared. */
        Integer scriptVersionNo,
        /** Composition version rendered; null when rendered straight from the script. */
        Integer compositionRevision
    ) {
        public static RenderJobResponse from(RenderJob job, Integer estimatedSeconds) {
            return from(job, estimatedSeconds, null);
        }

        public static RenderJobResponse from(RenderJob job, Integer estimatedSeconds, Integer scriptVersionNo) {
            return new RenderJobResponse(
                job.getId(),
                job.getProject().getId(),
                job.getStatus(),
                job.getProgress(),
                job.getStage(),
                job.getVideoUrl(),
                job.getErrorMessage(),
                estimatedSeconds,
                job.getCreatedAt(),
                scriptVersionNo,
                job.getCompositionRevision()
            );
        }
    }

    // ------------------------------------------------------------ composition

    /**
     * The producer's draft of an approved script. {@code revision} 0 means
     * nothing has been saved yet and the scenes are the approved script's.
     */
    public record CompositionResponse(
        String projectId,
        Integer scriptVersionNo,
        Integer revision,
        boolean editable,
        /** Why the draft cannot change right now; null when editable. */
        String lockedReason,
        List<Map<String, Object>> scenes,
        LocalDateTime updatedAt,
        String updatedByName
    ) {}

    public record UpdateCompositionRequest(
        /** The revision the edit started from; a different current revision is a conflict. */
        @NotNull(message = "baseRevision is required")
        @Min(value = 0, message = "baseRevision must not be negative")
        Integer baseRevision,

        @NotEmpty(message = "scenes are required")
        List<@Valid SceneRequest> scenes
    ) {}

    /** Progress pushed back by the Node render worker. */
    public record RenderCallbackRequest(
        @NotBlank(message = "jobId is required")
        String jobId,
        RenderStatus status,
        Integer progress,
        String stage,
        String videoUrl,
        String errorMessage
    ) {}

    // ---------------------------------------------------------------- publish

    public record PublishRequest(
        @NotEmpty(message = "at least one target is required")
        List<PublishTarget> targets,

        @NotBlank(message = "title is required")
        String title,

        String description
    ) {}

    public record PublishResponse(
        String id,
        String projectId,
        String lmsEmbedCode,
        String youtubeUrl,
        LocalDateTime publishedAt
    ) {
        public static PublishResponse from(PublishRecord record) {
            return new PublishResponse(
                record.getId(),
                record.getProject().getId(),
                record.getLmsEmbedCode(),
                record.getYoutubeUrl(),
                record.getCreatedAt()
            );
        }
    }

    // --------------------------------------------------------------- template

    public record TemplateRequest(
        @NotBlank(message = "name is required")
        String name,
        String category,
        String propsSchema,
        String previewUrl,
        Boolean isActive,
        String subject,
        String assetPack,
        String slots
    ) {}

    public record TemplateResponse(
        String id,
        String name,
        String category,
        String propsSchema,
        String previewUrl,
        Boolean isActive,
        String subject,
        String assetPack,
        String slots
    ) {
        public static TemplateResponse from(RemotionTemplate template) {
            return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getCategory(),
                template.getPropsSchema(),
                template.getPreviewUrl(),
                template.getIsActive(),
                template.getSubject(),
                template.getAssetPack(),
                template.getSlots()
            );
        }
    }
}
```

### FeedbackDtos.java

Nguồn: [FeedbackDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/review/dto/FeedbackDtos.java)

```java
package com.stemotion.domain.review.dto;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.review.entity.FeedbackComment;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class FeedbackDtos {

    private FeedbackDtos() {}

    /**
     * Either a clip pin (timestampSec set) or an inline script note (sceneId set).
     */
    public record FeedbackRequest(
        Double timestampSec,
        String sceneId,

        @NotBlank(message = "content is required")
        String content,

        String tag
    ) {}

    /** Mirrors the front end's FeedbackComment interface. */
    public record FeedbackResponse(
        String id,
        String author,
        String avatar,
        User.UserRole role,
        Double timestampSec,
        String sceneId,
        String content,
        String tag,
        FeedbackStatus status,
        LocalDateTime createdAt
    ) {
        public static FeedbackResponse from(FeedbackComment comment) {
            User author = comment.getAuthor();
            return new FeedbackResponse(
                comment.getId(),
                author.getFullName(),
                initialsOf(author.getFullName()),
                author.getRole(),
                comment.getTimestampSec(),
                comment.getScene() == null ? null : comment.getScene().getId(),
                comment.getContent(),
                comment.getTag(),
                comment.getStatus(),
                comment.getCreatedAt()
            );
        }

        /** The UI shows initials when a user has no uploaded avatar. */
        private static String initialsOf(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "?";
            }
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, 1).toUpperCase();
            }
            return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
    }
}
```

### QaDtos.java

Nguồn: [QaDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/review/dto/QaDtos.java)

```java
package com.stemotion.domain.review.dto;

import java.util.List;

public final class QaDtos {

    private QaDtos() {}

    /** How bad a finding is. FAIL blocks publishing; WARN is advisory. */
    public enum QaSeverity {
        PASS,
        WARN,
        FAIL
    }

    /** The automated checks listed in the project proposal. */
    public enum QaCheck {
        DURATION_BOUNDS,
        NARRATION_PACING,
        ON_SCREEN_TEXT_OVERFLOW,
        CONTRAST_READABILITY,
        SCENE_STRUCTURE,
        RENDER_FRESHNESS
    }

    public record QaFinding(
        QaCheck check,
        QaSeverity severity,
        String sceneId,
        String message,
        String suggestion
    ) {}

    /**
     * Result of a pre-publish sweep. {@code passed} is false as soon as any
     * check fails, which is what the publish endpoint gates on.
     */
    public record QaReport(
        String projectId,
        boolean passed,
        int failCount,
        int warnCount,
        List<QaFinding> findings
    ) {}
}
```

### SceneDtos.java

Nguồn: [SceneDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/script/dto/SceneDtos.java)

```java
package com.stemotion.domain.script.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.stemotion.core.enums.StemEnums.SceneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scene payloads in the shape the front end's SceneData union expects:
 * the common SceneBase fields plus the template-specific props flattened
 * onto the same object rather than nested under a "props" key.
 */
public final class SceneDtos {

    private SceneDtos() {}

    /**
     * Incoming scene. Any field that is not part of SceneBase is collected
     * into {@link #getProps()} and persisted as JSONB.
     */
    @Getter
    @Setter
    public static class SceneRequest {

        /** Existing server ID; local non-UUID IDs are temporary identities for new scenes. */
        private String id;

        @NotNull(message = "type is required")
        private SceneType type;

        @NotBlank(message = "title is required")
        private String title;

        private String narration;

        @NotNull(message = "durationInFrames is required")
        private Integer durationInFrames;

        /** Explicit order; when null the position in the submitted array wins. */
        private Integer orderNo;

        @JsonIgnore
        private final Map<String, Object> props = new LinkedHashMap<>();

        @JsonAnySetter
        public void putProp(String key, Object value) {
            props.put(key, value);
        }
    }

    /** Outgoing scene, flattened back into the SceneData shape. */
    @Getter
    public static class SceneResponse {

        private final String id;
        private final SceneType type;
        private final String title;
        private final String narration;
        private final Integer durationInFrames;

        @JsonIgnore
        private final Map<String, Object> props;

        public SceneResponse(
            String id,
            SceneType type,
            String title,
            String narration,
            Integer durationInFrames,
            Map<String, Object> props
        ) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.narration = narration;
            this.durationInFrames = durationInFrames;
            this.props = props == null ? Map.of() : props;
        }

        @JsonAnyGetter
        public Map<String, Object> getFlattenedProps() {
            return props;
        }
    }
}
```

### ScriptDtos.java

Nguồn: [ScriptDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/script/dto/ScriptDtos.java)

```java
package com.stemotion.domain.script.dto;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class ScriptDtos {

    private ScriptDtos() {}

    /** Creates a clip project inside a workspace. */
    public record CreateScriptRequest(
        @NotBlank(message = "title is required")
        String title,

        @NotNull(message = "subject is required")
        StemSubject subject,

        String gradeLevel,

        Integer fps,

        @Valid
        List<SceneRequest> scenes
    ) {}

    /** Saves a draft: metadata plus the full ordered scene list. */
    public record UpdateScriptRequest(
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer fps,
        String summary,

        @Valid
        List<SceneRequest> scenes
    ) {}

    /** Mirrors the front end's STEMScript interface. */
    public record ScriptResponse(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        ScriptStatus scriptStatus,
        VideoStatus videoStatus,
        Integer fps,
        List<SceneResponse> scenes,
        String workspaceId,
        String ownerId,
        String ownerName,
        Integer currentVersionNo,
        String videoUrl,
        LocalDateTime createdAt
    ) {}

    /** Lightweight row for dashboards and the pipeline board. */
    public record ScriptSummaryResponse(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        ScriptStatus scriptStatus,
        VideoStatus videoStatus,
        String ownerName,
        Integer sceneCount,
        LocalDateTime updatedAt
    ) {}

    /** Reviewer decision payload for approve / request-changes. */
    public record ReviewDecisionRequest(
        String note
    ) {}

    public record ScriptVersionResponse(
        String id,
        Integer versionNo,
        String title,
        String summary,
        ScriptStatus status,
        String reviewNote,
        Boolean isCurrent,
        String createdById,
        String createdByName,
        LocalDateTime createdAt
    ) {}

    /** Side-by-side payload backing the version diff modal. */
    public record VersionDiffResponse(
        ScriptVersionResponse left,
        List<SceneResponse> leftScenes,
        ScriptVersionResponse right,
        List<SceneResponse> rightScenes
    ) {}
}
```

### WorkspaceDtos.java

Nguồn: [WorkspaceDtos.java](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/dto/WorkspaceDtos.java)

```java
package com.stemotion.domain.workspace.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record WorkspaceRequest(
        @NotBlank(message = "name is required")
        String name,
        String department,
        @NotBlank(message = "leaderUserId is required")
        String leaderUserId,
        User.UserRole leaderRole
    ) {}

    public record WorkspaceResponse(
        String id,
        String name,
        String department,
        long membersCount,
        long activeProjects,
        User.UserRole myRole,
        Boolean isLeader
    ) {}

    public record AddMemberRequest(
        @NotBlank(message = "userId is required")
        String userId,

        @NotNull(message = "role is required")
        User.UserRole role,

        /** The front end sends "isLeader", the name used in every response. */
        @JsonAlias("isLeader")
        Boolean leader
    ) {}

    public record UpdateMemberRequest(
        User.UserRole role,
        MemberStatus status,
        @JsonAlias("isLeader")
        Boolean leader
    ) {}

    public record MemberResponse(
        String id,
        String userId,
        String name,
        String email,
        User.UserRole role,
        boolean isLeader,
        MemberStatus status
    ) {
        public static MemberResponse from(WorkspaceMember member) {
            User user = member.getUser();
            return new MemberResponse(
                member.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                member.getRole(),
                member.isLeader(),
                member.getStatus()
            );
        }
    }

    public record CandidateUserResponse(
        String id,
        String fullName,
        String email
    ) {
        public static CandidateUserResponse from(User user) {
            return new CandidateUserResponse(user.getId(), user.getFullName(), user.getEmail());
        }
    }
}
```

### Nested records: AiUsageService.java

```java
public record UsageGroup(LocalDate day, String model, String userId, long requestCount,
        Long tokensIn, Long tokensOut, long unknownTokenRequests, long failedRequests,
        BigDecimal estimatedCostUsd, BigDecimal knownEstimatedCostUsd, long unknownCostRequests) {}
public record UsagePage(List<UsageGroup> content, int page, int size, long totalElements, int totalPages,
        LocalDate from, LocalDate to, String timezone) {}
```

### Nested records: LibraryService.java

```java
public record LibraryFilters(List<StemSubject> subjects, List<String> gradeLevels) {}
```

## 4. FastAPI model/env/alignment response source

### models.py

Nguồn: [models.py](../STEMotion_BE/services/ai/app/models.py)

```python
from typing import Any, Literal
from pydantic import BaseModel, Field, ConfigDict

Operation = Literal["generate", "resegment", "grade", "extract", "terms"]
Subject = Literal["Math", "Physics", "Chemistry", "Biology", "ComputerScience", "CS"]

class GenerationRequest(BaseModel):
    systemInstruction: str = Field(min_length=1, max_length=20000)
    prompt: str = Field(min_length=1, max_length=80000)
    responseSchema: dict[str, Any]
    task: Operation = "generate"

class GenerationResponse(BaseModel):
    result: dict[str, Any]
    model: str
    elapsedMs: int
    inputTokens: int | None = Field(default=None, ge=0)
    outputTokens: int | None = Field(default=None, ge=0)
    totalTokens: int | None = Field(default=None, ge=0)

class ScriptInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    topic: str = Field(min_length=1, max_length=1000)
    subject: Subject
    gradeLevel: str = Field(min_length=1, max_length=64)
    targetDurationSec: int | None = Field(default=None, ge=0, le=300)
    narrativeBrief: str | None = Field(default=None, max_length=500)
    approach: str | None = Field(default=None, max_length=800)

class AnalysisInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    scriptText: str = Field(min_length=1, max_length=60000)
    subject: Subject | None = None
    gradeLevel: str | None = Field(default=None, max_length=64)

class ActionInput(AnalysisInput):
    action: Literal["resegment", "grade", "extract", "terms"]
```

### deployment.py

Nguồn: [deployment.py](../STEMotion_BE/services/ai/app/deployment.py)

```python
"""Deployment checks only: no inference, provider calls, or model downloads."""
import os
from pathlib import Path


def shared_key():
    # New deployment name takes precedence; keep the Phase 3 name for rollback.
    return os.getenv("AI_SERVICE_SHARED_KEY") or os.getenv("AI_SERVICE_KEY", "")


def model_directory():
    return Path(os.getenv("WHISPER_MODEL_DIR") or "/opt/whisper/small")


def weights_present():
    directory = model_directory()
    return all((directory / name).is_file() and (directory / name).stat().st_size > 0
               for name in ("model.bin", "config.json", "tokenizer.json"))


def configured():
    provider = os.getenv("AI_PROVIDER", "openai")
    key_name = {"openai": "OPENAI_API_KEY", "gemini": "GEMINI_API_KEY"}.get(provider)
    return bool(shared_key() and key_name and os.getenv(key_name))


def readiness():
    # launch sets this only after bounded, offline model loading has succeeded.
    ready = configured() and weights_present() and os.getenv("AI_WHISPER_VERIFIED") == "true"
    return {"status": "READY" if ready else "NOT_READY", "configured": configured(),
            "weightsPresent": weights_present(), "whisperVerified": os.getenv("AI_WHISPER_VERIFIED") == "true"}
```

### launch.py

Nguồn: [launch.py](../STEMotion_BE/services/ai/app/launch.py)

```python
"""Bounded offline preflight, then exec uvicorn as PID 1 (no shell interpolation)."""
import logging
import os
import subprocess
import sys
from .deployment import model_directory, weights_present

log = logging.getLogger("ai.startup")


def preflight():
    os.environ.pop("AI_WHISPER_VERIFIED", None)
    mode = os.getenv("WHISPER_STARTUP_MODE", "required")
    if mode not in {"required", "optional"}:
        raise RuntimeError("WHISPER_STARTUP_MODE must be required or optional")
    # Deployment never downloads weights; provision before starting the container.
    os.environ["WHISPER_LOCAL_FILES_ONLY"] = "true"
    os.environ["HF_HUB_OFFLINE"] = "1"
    if not weights_present():
        message = "Whisper weights missing: WHISPER_MODEL_DIR must contain model.bin, config.json and tokenizer.json; provision bake/volume first"
        if mode == "required":
            raise RuntimeError(message)
        log.warning("%s; optional smoke mode: /ready stays 503", message)
        return
    os.environ["WHISPER_MODEL"] = str(model_directory().resolve())
    try:
        timeout = int(os.getenv("WHISPER_STARTUP_TIMEOUT_SEC", "90"))
        if not 1 <= timeout <= 600:
            raise ValueError()
    except ValueError:
        raise RuntimeError("WHISPER_STARTUP_TIMEOUT_SEC must be 1..600") from None
    try:
        # Child is killed on timeout. No ASR, inference or outbound network access.
        subprocess.run([sys.executable, "-c", "from app.alignment import load_model; load_model()"],
                       check=True, timeout=timeout, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except subprocess.TimeoutExpired:
        raise RuntimeError("Whisper offline model loading timed out; check weights and memory") from None
    except subprocess.CalledProcessError:
        raise RuntimeError("Whisper offline model loading failed; check model format, CPU runtime and available RAM") from None
    os.environ["AI_WHISPER_VERIFIED"] = "true"
    log.info("Whisper offline preflight passed (no inference performed)")


def main():
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    try:
        workers = int(os.getenv("UVICORN_WORKERS", "1"))
        port = int(os.getenv("PORT", "8000"))
        if workers < 1 or not 1 <= port <= 65535:
            raise RuntimeError("UVICORN_WORKERS must be positive; PORT must be 1..65535")
        preflight()
    except (RuntimeError, ValueError) as exc:
        log.error("Startup rejected: %s", exc)
        return 1
    args = [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0",
            "--port", str(port), "--workers", str(workers)]
    os.execvp(sys.executable, args)


if __name__ == "__main__":
    sys.exit(main())
```

### alignment.py

Nguồn: [alignment.py](../STEMotion_BE/services/ai/app/alignment.py)

```python
"""Known-transcript forced alignment. Never invokes Whisper transcription."""
import io
import math
import os
from functools import lru_cache
from threading import Lock
from fastapi import HTTPException

MODEL_LOCK = Lock()
MAX_AUDIO_BYTES = 10 * 1024 * 1024

@lru_cache(maxsize=1)
def load_model():
    from faster_whisper import WhisperModel
    # Production should pre-provision weights; no key is required for local models.
    return WhisperModel(os.getenv("WHISPER_MODEL", "small"),
        device=os.getenv("WHISPER_DEVICE", "cpu"), compute_type=os.getenv("WHISPER_COMPUTE_TYPE", "int8"),
        download_root=os.getenv("WHISPER_MODEL_DIR") or None,
        local_files_only=os.getenv("WHISPER_LOCAL_FILES_ONLY", "true").lower() == "true")

class WhisperAligner:
    def align(self, audio_bytes: bytes, text: str, language: str):
        from faster_whisper.audio import decode_audio, pad_or_trim
        from faster_whisper.tokenizer import Tokenizer
        from faster_whisper.transcribe import merge_punctuations
        try:
            audio = decode_audio(io.BytesIO(audio_bytes), sampling_rate=16000)
        except Exception:
            raise HTTPException(422, "Audio could not be decoded") from None
        duration = len(audio) / 16000
        if not math.isfinite(duration) or duration < 0.1 or duration > 30:
            raise HTTPException(422, "Align one scene at a time: audio must be 0.1 to 30 seconds")
        # One cached model; bounded concurrent access keeps encoder/decoder memory stable.
        with MODEL_LOCK:
            try:
                model = load_model()
            except Exception:
                raise HTTPException(503, "Alignment model is unavailable; provision WHISPER_MODEL weights") from None
            try:
                tokenizer = Tokenizer(model.hf_tokenizer, model.model.is_multilingual, task="transcribe", language=language)
                tokens = tokenizer.encode(text)
            except Exception:
                raise HTTPException(422, "Unsupported alignment language or text") from None
            if not tokens or len(tokens) > model.max_length - len(tokenizer.sot_sequence) - 1:
                raise HTTPException(422, "Transcript exceeds the alignment context; split the scene")
            features = model.feature_extractor(audio)
            frames = min(round(len(audio) / model.feature_extractor.hop_length), model.feature_extractor.nb_max_frames)
            encoded = model.encode(pad_or_trim(features))
            # CT2 attention/DTW alignment of supplied text tokens; no ASR generate/transcribe.
            alignment = model.find_alignment(tokenizer, [tokens], encoded, frames)[0]
            # Punctuation may have zero duration: attach it to its spoken word first.
            merge_punctuations(alignment, "\"\'“¿([{-", "\"\'.。,，!！?？:：”)]}、")
        return format_alignment(alignment, text, duration, os.getenv("WHISPER_MODEL", "small"))

def format_alignment(alignment, text, duration, model):
    if not alignment:
        raise HTTPException(422, "No reliable word alignment was found")
    raw_text = "".join(word["word"] for word in alignment)
    if "".join(raw_text.split()) != "".join(text.split()):
        raise HTTPException(422, "Aligned text does not match the supplied narration")
    words = []
    previous_end = 0.0
    for word in alignment:
        label = word["word"].strip()
        if not label:
            continue
        start, end = float(word["start"]), float(word["end"])
        if (not math.isfinite(start) or not math.isfinite(end) or start < previous_end - 0.02
                or start < 0 or end <= start or end > duration + 0.05
                or float(word.get("probability", 0)) < float(os.getenv("WHISPER_MIN_WORD_PROBABILITY", "0.05"))):
            raise HTTPException(422, "Low confidence or invalid word timestamps; review the narration/audio")
        start, end = max(previous_end, start), min(end, duration)
        if end <= start:
            raise HTTPException(422, "Invalid word duration")
        words.append({"text": label, "start": round(start, 3), "end": round(end, 3)})
        previous_end = end
    if not words:
        raise HTTPException(422, "No words were aligned")
    return {"words": words, "segments": [{"text": text, "start": words[0]["start"],
        "end": words[-1]["end"], "words": words}], "model": model, "durationSec": duration,
        "method": "forced-alignment", "tokensIn": None, "tokensOut": None}

def aligner():
    return WhisperAligner()
```

### Tất cả env access trong app/

```text
alignment.py:16: return WhisperModel(os.getenv("WHISPER_MODEL", "small"),
alignment.py:17: device=os.getenv("WHISPER_DEVICE", "cpu"), compute_type=os.getenv("WHISPER_COMPUTE_TYPE", "int8"),
alignment.py:18: download_root=os.getenv("WHISPER_MODEL_DIR") or None,
alignment.py:19: local_files_only=os.getenv("WHISPER_LOCAL_FILES_ONLY", "true").lower() == "true")
alignment.py:53: return format_alignment(alignment, text, duration, os.getenv("WHISPER_MODEL", "small"))
alignment.py:70: or float(word.get("probability", 0)) < float(os.getenv("WHISPER_MIN_WORD_PROBABILITY", "0.05"))):
deployment.py:8: return os.getenv("AI_SERVICE_SHARED_KEY") or os.getenv("AI_SERVICE_KEY", "")
deployment.py:12: return Path(os.getenv("WHISPER_MODEL_DIR") or "/opt/whisper/small")
deployment.py:22: provider = os.getenv("AI_PROVIDER", "openai")
deployment.py:24: return bool(shared_key() and key_name and os.getenv(key_name))
deployment.py:29: ready = configured() and weights_present() and os.getenv("AI_WHISPER_VERIFIED") == "true"
deployment.py:31: "weightsPresent": weights_present(), "whisperVerified": os.getenv("AI_WHISPER_VERIFIED") == "true"}
launch.py:12: os.environ.pop("AI_WHISPER_VERIFIED", None)
launch.py:13: mode = os.getenv("WHISPER_STARTUP_MODE", "required")
launch.py:17: os.environ["WHISPER_LOCAL_FILES_ONLY"] = "true"
launch.py:18: os.environ["HF_HUB_OFFLINE"] = "1"
launch.py:25: os.environ["WHISPER_MODEL"] = str(model_directory().resolve())
launch.py:27: timeout = int(os.getenv("WHISPER_STARTUP_TIMEOUT_SEC", "90"))
launch.py:40: os.environ["AI_WHISPER_VERIFIED"] = "true"
launch.py:47: workers = int(os.getenv("UVICORN_WORKERS", "1"))
launch.py:48: port = int(os.getenv("PORT", "8000"))
main.py:26: name = os.getenv("AI_PROVIDER", "openai")
main.py:37: name = os.getenv("AI_PROVIDER", "openai")
providers.py:10: key = os.getenv("GEMINI_API_KEY", "")
providers.py:18: model = os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite")
providers.py:38: key = os.getenv("OPENAI_API_KEY", "").strip()
providers.py:39: model = os.getenv("OPENAI_MODEL_DRAFT", "gpt-4o") if request.task == "generate" else os.getenv("OPENAI_MODEL_LIGHT", "gpt-4o-mini")
```
