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
