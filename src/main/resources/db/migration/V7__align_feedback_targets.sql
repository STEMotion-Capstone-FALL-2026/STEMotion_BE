-- Preserve legacy clip/version targets while accepting the current project's
-- rendered-video target. No clips table row is invented for a render job.
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS target_type VARCHAR(30);
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS script_version_id VARCHAR(36);
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS scene_key VARCHAR(255);
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS timestamp_val DOUBLE PRECISION;
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS clip_id VARCHAR(36);

UPDATE feedback_comments f SET
    target_type = 'SCRIPT_INLINE', script_version_id = s.version_id, scene_key = s.id::text
FROM scenes s WHERE f.scene_id = s.id AND f.target_type IS NULL;
UPDATE feedback_comments SET target_type = 'CLIP_TIMESTAMP', timestamp_val = timestamp_sec
WHERE target_type IS NULL AND scene_id IS NULL AND timestamp_sec IS NOT NULL;

ALTER TABLE feedback_comments ALTER COLUMN target_type SET NOT NULL;
ALTER TABLE feedback_comments DROP CONSTRAINT IF EXISTS chk_feedback_target;
ALTER TABLE feedback_comments ADD CONSTRAINT chk_feedback_target CHECK (
    (target_type = 'SCRIPT_INLINE' AND script_version_id IS NOT NULL AND scene_key IS NOT NULL)
    OR (target_type = 'CLIP_TIMESTAMP' AND clip_id IS NOT NULL AND timestamp_val IS NOT NULL)
    OR (target_type = 'CLIP_TIMESTAMP' AND project_id IS NOT NULL AND timestamp_sec IS NOT NULL AND timestamp_sec >= 0)
) NOT VALID;
ALTER TABLE feedback_comments VALIDATE CONSTRAINT chk_feedback_target;
