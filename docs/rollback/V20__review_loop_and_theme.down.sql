-- Manual rollback on the Flyway baseline, after reviewing backup and application
-- compatibility. V20 added resolved_by / resolved_at here, so they go too.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM status_histories WHERE target_type = 'COMPOSITION') THEN
        RAISE EXCEPTION 'Web-player review decisions (target COMPOSITION) exist; export them before rollback';
    END IF;
    IF EXISTS (SELECT 1 FROM feedback_comments WHERE parent_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Replies exist; export them before rollback';
    END IF;
END $$;
ALTER TABLE status_histories DROP CONSTRAINT IF EXISTS status_history_event_shape;
ALTER TABLE status_histories ADD CONSTRAINT status_history_event_shape CHECK (
    (event_type = 'PIPELINE_TRANSITION' AND target_type = 'PROJECT' AND decision IS NULL)
    OR (event_type = 'REVIEW_DECISION' AND target_type IN ('SCRIPT', 'CLIP')
        AND decision IS NOT NULL AND decision IN ('APPROVED', 'REJECTED')));
DROP INDEX IF EXISTS idx_feedback_comments_parent;
DROP INDEX IF EXISTS idx_feedback_comments_clip;
ALTER TABLE feedback_comments DROP COLUMN IF EXISTS parent_id;
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_visual_theme_check;
ALTER TABLE compositions DROP COLUMN IF EXISTS visual_theme;
ALTER TABLE remotion_templates DROP CONSTRAINT IF EXISTS remotion_templates_recommended_subjects_check;
ALTER TABLE remotion_templates DROP CONSTRAINT IF EXISTS remotion_templates_default_visual_theme_check;
ALTER TABLE remotion_templates DROP COLUMN IF EXISTS recommended_subjects;
ALTER TABLE remotion_templates DROP COLUMN IF EXISTS default_visual_theme;
ALTER TABLE feedback_comments DROP COLUMN IF EXISTS resolved_at;
ALTER TABLE feedback_comments DROP COLUMN IF EXISTS resolved_by;
