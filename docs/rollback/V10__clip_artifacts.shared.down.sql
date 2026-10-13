-- Supabase/diagram baseline: clips existed BEFORE V10. NEVER drop that table.
-- Deploy the old BE first. Use only if the pre-migration schema backup confirms
-- updated_at did not exist; otherwise restore its original definition instead.
BEGIN;
-- Refuses to restore NOT NULL if new artifacts have unknown duration. Measure
-- those artifacts or restore the pre-migration backup; do not fill fake zeros.
ALTER TABLE clips ALTER COLUMN duration_sec SET NOT NULL;
ALTER TABLE clips ALTER COLUMN qa_report SET DEFAULT '{}'::jsonb;
ALTER TABLE clips DROP CONSTRAINT clips_measured_duration_check;
ALTER TABLE clips DROP CONSTRAINT clips_artifact_url_check;
ALTER TABLE clips DROP COLUMN updated_at;
COMMIT;
-- Backfilled artifacts are intentionally retained: deleting them could destroy
-- later review/publication references. Source jobs and original artifacts remain.
-- Reconcile Flyway version history explicitly before reapplying V10.
