-- Export populated metadata and take a backup before rollback. Never silently lose it.
BEGIN;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM remotion_templates WHERE subject IS NOT NULL OR asset_pack IS NOT NULL OR slots IS NOT NULL) THEN
    RAISE EXCEPTION 'Director metadata exists: export/clear it explicitly before rollback';
  END IF;
END $$;
ALTER TABLE remotion_templates DROP CONSTRAINT ck_template_slots_object;
ALTER TABLE remotion_templates DROP COLUMN slots, DROP COLUMN asset_pack, DROP COLUMN subject;
COMMIT;
