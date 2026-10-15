-- Rollback for V16__admin_provisioned_membership.sql
-- Take a full database backup before executing this rollback script.
BEGIN;

-- Check if any members have no corresponding email to restore invited_email
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM workspace_members m LEFT JOIN users u ON u.id = m.user_id WHERE u.email IS NULL) THEN
        RAISE EXCEPTION 'Cannot rollback V16: some workspace members cannot be mapped back to a user email';
    END IF;
END $$;

-- 1. Re-add dropped columns to workspace_members
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS invited_email VARCHAR(255);
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS invitation_token_hash VARCHAR(64);
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS invitation_expires_at TIMESTAMPTZ;

-- Backfill invited_email from user email
UPDATE workspace_members m
SET invited_email = u.email
FROM users u
WHERE m.user_id = u.id AND m.invited_email IS NULL;

ALTER TABLE workspace_members ALTER COLUMN invited_email SET NOT NULL;
ALTER TABLE workspace_members ALTER COLUMN user_id DROP NOT NULL;

-- 2. Restore constraints and indexes
ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS uk_workspace_member_user;
ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS uk_workspace_member_email;
ALTER TABLE workspace_members ADD CONSTRAINT uk_workspace_member_email UNIQUE (workspace_id, invited_email);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_invitation_token
    ON workspace_members (invitation_token_hash) WHERE invitation_token_hash IS NOT NULL;

-- 3. Restore role check to allow ADMIN in workspace
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'role';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_role_check;
        ALTER TABLE workspace_members ADD CONSTRAINT workspace_members_role_check
            CHECK (role::text IN ('WRITER', 'REVIEWER', 'PRODUCER', 'ADMIN'));
    END IF;
END $$;

-- 4. Restore status values and check:
-- Drop V16 status check first so 'INVITED' is accepted during update
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'status';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_status_check;
    END IF;
END $$;

-- Convert INACTIVE back to INVITED
UPDATE workspace_members SET status = 'INVITED' WHERE status = 'INACTIVE';

-- Add restored constraint ('ACTIVE', 'INVITED')
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'status';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE workspace_members ADD CONSTRAINT workspace_members_status_check
            CHECK (status::text IN ('ACTIVE', 'INVITED'));
    END IF;
END $$;

-- 5. Drop added columns
ALTER TABLE workspace_members DROP COLUMN IF EXISTS is_leader;
ALTER TABLE users DROP COLUMN IF EXISTS must_change_password;

COMMIT;
