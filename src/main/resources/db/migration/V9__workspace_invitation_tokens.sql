-- Legacy pending seats remain inactive until their administrator reissues a link.
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS invitation_token_hash VARCHAR(64);
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS invitation_expires_at TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_invitation_token
    ON workspace_members (invitation_token_hash) WHERE invitation_token_hash IS NOT NULL;
