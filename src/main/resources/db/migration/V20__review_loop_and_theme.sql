-- Review loop and production theme (FE checklist BE-P0-1, BE-P0-5, BE-P1-1,
-- BE-P1-2, BE-P1-4).
--
-- * compositions.visual_theme: the producer's visual theme, part of the
--   composition a render draws, so a re-render or reopen keeps it.
-- * status_histories: a reviewer may approve or return the composition
--   revision reviewed in the web player before any MP4 exists (target
--   COMPOSITION), besides a script version or a rendered Clip.
-- * feedback_comments.parent_id: replies under a review note; resolved_by /
--   resolved_at record who closed a note and when (the shared Supabase
--   baseline already has these two; the Flyway baseline gets them).
-- * remotion_templates: subjects a template is recommended for and its
--   default visual theme, edited from the admin portal.
--
-- Key columns follow the type of the table they reference: uuid on the shared
-- Supabase baseline, varchar(36) on the Flyway baseline.

ALTER TABLE compositions ADD COLUMN IF NOT EXISTS visual_theme varchar(32) NOT NULL DEFAULT 'default';
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_visual_theme_check;
ALTER TABLE compositions ADD CONSTRAINT compositions_visual_theme_check
    CHECK (visual_theme IN ('default', 'cyber', 'emerald', 'sunset', 'academic'));

ALTER TABLE status_histories DROP CONSTRAINT IF EXISTS status_history_event_shape;
ALTER TABLE status_histories ADD CONSTRAINT status_history_event_shape CHECK (
    (event_type = 'PIPELINE_TRANSITION' AND target_type = 'PROJECT' AND decision IS NULL)
    OR (event_type = 'REVIEW_DECISION' AND target_type IN ('SCRIPT', 'CLIP', 'COMPOSITION')
        AND decision IS NOT NULL AND decision IN ('APPROVED', 'REJECTED')));

DO $$
DECLARE comment_id_type text; user_id_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO comment_id_type FROM pg_attribute
        WHERE attrelid = 'feedback_comments'::regclass AND attname = 'id' AND NOT attisdropped;
    SELECT format_type(atttypid, atttypmod) INTO user_id_type FROM pg_attribute
        WHERE attrelid = 'users'::regclass AND attname = 'id' AND NOT attisdropped;
    EXECUTE format('ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS parent_id %s
        REFERENCES feedback_comments(id) ON DELETE CASCADE', comment_id_type);
    EXECUTE format('ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS resolved_by %s
        REFERENCES users(id) ON DELETE SET NULL', user_id_type);
END $$;
ALTER TABLE feedback_comments ADD COLUMN IF NOT EXISTS resolved_at timestamp with time zone;
CREATE INDEX IF NOT EXISTS idx_feedback_comments_parent ON feedback_comments (parent_id);
CREATE INDEX IF NOT EXISTS idx_feedback_comments_clip ON feedback_comments (clip_id);

ALTER TABLE remotion_templates ADD COLUMN IF NOT EXISTS recommended_subjects jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE remotion_templates DROP CONSTRAINT IF EXISTS remotion_templates_recommended_subjects_check;
ALTER TABLE remotion_templates ADD CONSTRAINT remotion_templates_recommended_subjects_check
    CHECK (jsonb_typeof(recommended_subjects) = 'array');
ALTER TABLE remotion_templates ADD COLUMN IF NOT EXISTS default_visual_theme varchar(32);
ALTER TABLE remotion_templates DROP CONSTRAINT IF EXISTS remotion_templates_default_visual_theme_check;
ALTER TABLE remotion_templates ADD CONSTRAINT remotion_templates_default_visual_theme_check
    CHECK (default_visual_theme IS NULL
        OR default_visual_theme IN ('default', 'cyber', 'emerald', 'sunset', 'academic'));
