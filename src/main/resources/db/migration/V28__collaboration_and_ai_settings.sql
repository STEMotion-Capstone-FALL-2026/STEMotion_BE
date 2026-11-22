-- V28__collaboration_and_ai_settings.sql
-- Project proposals, project members and join requests, edit leases, contribution events (UC-29 to UC-33)
-- and the Admin AI/TTS settings with budgets (UC-34). Design: Report 4 sections 3.10 to 3.12, tables 28 to 33;
-- decisions D04/D07, D08/D09, D12 to D18 and D-ADM agreed by the team on 22/11/2026.
-- Like V27, ids are varchar(36) and there are no foreign keys, so the script runs on both the
-- VARCHAR(36) dev/test schema and the UUID production schema. Production applies it by hand (Flyway off).

CREATE TABLE IF NOT EXISTS project_proposals (
    id varchar(36) PRIMARY KEY,
    workspace_id varchar(36) NOT NULL,
    proposer_id varchar(36) NOT NULL,
    title varchar(255) NOT NULL,
    subject varchar(32) NOT NULL,
    grade_level varchar(32),
    requested_roles varchar(128) NOT NULL DEFAULT 'WRITER,REVIEWER,PRODUCER',
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    decision_reason text,
    decided_by varchar(36),
    decided_at timestamp(6),
    project_id varchar(36),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_project_proposal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_project_proposals_workspace_status ON project_proposals (workspace_id, status);

CREATE TABLE IF NOT EXISTS project_members (
    id varchar(36) PRIMARY KEY,
    project_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    role varchar(16) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    revoked_by varchar(36),
    revoked_at timestamp(6),
    created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_member_user UNIQUE (project_id, user_id),
    CONSTRAINT ck_project_member_role CHECK (role IN ('WRITER', 'REVIEWER', 'PRODUCER')),
    CONSTRAINT ck_project_member_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);
CREATE INDEX IF NOT EXISTS idx_project_members_user ON project_members (user_id);

CREATE TABLE IF NOT EXISTS project_join_requests (
    id varchar(36) PRIMARY KEY,
    project_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    role varchar(16) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    reason text,
    decided_by varchar(36),
    decided_at timestamp(6),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_join_request_role CHECK (role IN ('WRITER', 'REVIEWER', 'PRODUCER')),
    CONSTRAINT ck_join_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED'))
);
CREATE INDEX IF NOT EXISTS idx_join_requests_project_status ON project_join_requests (project_id, status);

CREATE TABLE IF NOT EXISTS edit_leases (
    id varchar(36) PRIMARY KEY,
    resource_type varchar(16) NOT NULL,
    resource_id varchar(36) NOT NULL,
    holder_id varchar(36) NOT NULL,
    acquired_at timestamp(6) NOT NULL,
    renewed_at timestamp(6) NOT NULL,
    expires_at timestamp(6) NOT NULL,
    released_by varchar(36),
    created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_edit_lease_resource UNIQUE (resource_type, resource_id),
    CONSTRAINT ck_edit_lease_type CHECK (resource_type IN ('SCRIPT_DRAFT', 'COMPOSITION'))
);

CREATE TABLE IF NOT EXISTS contribution_events (
    id varchar(36) PRIMARY KEY,
    project_id varchar(36) NOT NULL,
    actor_id varchar(36) NOT NULL,
    project_role varchar(16) NOT NULL,
    event_type varchar(32) NOT NULL,
    target_id varchar(36),
    occurred_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_contribution_type CHECK (event_type IN
        ('SCRIPT_SUBMITTED', 'COMPOSITION_REVISED', 'REVIEW_DECIDED', 'NOTE_RESOLVED', 'PUBLISHED'))
);
CREATE INDEX IF NOT EXISTS idx_contribution_events_project ON contribution_events (project_id, occurred_at);

CREATE TABLE IF NOT EXISTS ai_provider_settings (
    id varchar(36) PRIMARY KEY,
    allowed_ai_providers text NOT NULL DEFAULT 'openai',
    allowed_models text NOT NULL DEFAULT '',
    allowed_voices text NOT NULL DEFAULT '',
    monthly_budget_usd numeric(10, 2) NOT NULL DEFAULT 20.00,
    per_clip_cap_usd numeric(10, 2) NOT NULL DEFAULT 0.50,
    revision integer NOT NULL DEFAULT 0,
    updated_by varchar(36),
    created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ai_budget_positive CHECK (monthly_budget_usd >= 0 AND per_clip_cap_usd >= 0)
);

-- Per-clip spending needs to know which project an AI call served.
ALTER TABLE ai_requests ADD COLUMN IF NOT EXISTS project_id varchar(36);
CREATE INDEX IF NOT EXISTS idx_ai_requests_project ON ai_requests (project_id);
