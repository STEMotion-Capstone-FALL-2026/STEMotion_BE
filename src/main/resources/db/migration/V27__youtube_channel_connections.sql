-- V27__youtube_channel_connections.sql
-- YouTube channel OAuth connections and publications upload tracking.
-- Compatible with both VARCHAR(36) (dev/test) and UUID (production) schemas.
-- Note: No foreign keys to workspaces, users, or clip_projects to ensure cross-schema compatibility.

CREATE TABLE IF NOT EXISTS youtube_channel_connections (
    id varchar(36) PRIMARY KEY,
    workspace_id varchar(36) NOT NULL,
    channel_id varchar(128) NOT NULL,
    channel_title text NOT NULL,
    refresh_token_ciphertext text NOT NULL,
    scopes text NOT NULL,
    connected_by varchar(36) NOT NULL,
    connected_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_youtube_channel_conn_workspace_id ON youtube_channel_connections (workspace_id);

ALTER TABLE publications ADD COLUMN IF NOT EXISTS external_id varchar(128);
ALTER TABLE publications ADD COLUMN IF NOT EXISTS external_url text;
ALTER TABLE publications ADD COLUMN IF NOT EXISTS upload_status varchar(32);
ALTER TABLE publications ADD COLUMN IF NOT EXISTS upload_error text;
ALTER TABLE publications ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 0;
