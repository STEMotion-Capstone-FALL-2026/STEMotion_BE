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
