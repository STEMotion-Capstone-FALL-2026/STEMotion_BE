-- Deploy old BE first. Back up history rows including explicit review fields.
-- Refuse lossy rollback after real reviews; restore/retain these fields instead.
BEGIN;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM status_histories WHERE event_type = 'REVIEW_DECISION') THEN
        RAISE EXCEPTION 'Review events exist; rollback would lose artifact binding';
    END IF;
END $$;
DROP TRIGGER protect_reviewed_clip ON clips;
DROP TRIGGER protect_reviewed_script ON script_versions;
DROP FUNCTION stemotion_protect_review_target();
DROP TRIGGER history_append_only ON status_histories;
DROP TRIGGER history_no_truncate ON status_histories;
DROP FUNCTION stemotion_reject_history_mutation();
DROP TRIGGER validate_history_target ON status_histories;
DROP FUNCTION stemotion_validate_history_target();
DROP INDEX idx_status_history_target;
ALTER TABLE status_histories DROP CONSTRAINT status_history_event_shape;
ALTER TABLE status_histories DROP COLUMN event_type, DROP COLUMN target_type,
    DROP COLUMN target_id, DROP COLUMN decision, DROP COLUMN reason;
COMMIT;
