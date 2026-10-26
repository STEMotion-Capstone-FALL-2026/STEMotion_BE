-- Rollback of V22: drops the draft revision. Same on the Flyway and the shared
-- Supabase baseline. Deploy a backend without ScriptVersion.draftRevision first.
ALTER TABLE script_versions DROP CONSTRAINT IF EXISTS script_versions_draft_revision_check;
ALTER TABLE script_versions DROP COLUMN IF EXISTS draft_revision;
