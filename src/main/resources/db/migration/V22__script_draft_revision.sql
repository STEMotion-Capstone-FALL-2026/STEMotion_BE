-- Draft revision of a script version (master update plan M17, "Revision").
--
-- A writer's save names the draft revision it was edited from; a save from an
-- older revision is refused with 409 instead of silently overwriting another
-- tab's or another writer's scenes. Composition saves already work this way
-- (compositions.revision). Existing rows start at 0.

ALTER TABLE script_versions ADD COLUMN IF NOT EXISTS draft_revision integer NOT NULL DEFAULT 0;
ALTER TABLE script_versions DROP CONSTRAINT IF EXISTS script_versions_draft_revision_check;
ALTER TABLE script_versions ADD CONSTRAINT script_versions_draft_revision_check CHECK (draft_revision >= 0);
