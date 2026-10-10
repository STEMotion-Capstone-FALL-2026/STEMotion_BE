ALTER TABLE status_histories ADD COLUMN event_type varchar(32) NOT NULL DEFAULT 'PIPELINE_TRANSITION';
ALTER TABLE status_histories ADD COLUMN target_type varchar(16) NOT NULL DEFAULT 'PROJECT';
ALTER TABLE status_histories ADD COLUMN target_id varchar(36);
ALTER TABLE status_histories ADD COLUMN decision varchar(16);
ALTER TABLE status_histories ADD COLUMN reason text;
UPDATE status_histories SET target_id = project_id::text;
ALTER TABLE status_histories ALTER COLUMN target_id SET NOT NULL;
ALTER TABLE status_histories ADD CONSTRAINT status_history_event_shape CHECK (
    (event_type = 'PIPELINE_TRANSITION' AND target_type = 'PROJECT' AND decision IS NULL)
    OR (event_type = 'REVIEW_DECISION' AND target_type IN ('SCRIPT','CLIP')
        AND decision IS NOT NULL AND decision IN ('APPROVED','REJECTED')));
CREATE INDEX idx_status_history_target ON status_histories(target_type,target_id,created_at);

-- Polymorphic target_id is textual to support UUID and legacy varchar parents.
-- Bind to a real artifact/version in this project, never the mutable project alone.
CREATE FUNCTION stemotion_validate_history_target() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Compatibility during rollout: the old BE inserts project transitions
    -- without target_id. Fill it before the NOT NULL check, preserving its API.
    IF NEW.target_id IS NULL AND NEW.event_type='PIPELINE_TRANSITION' AND NEW.target_type='PROJECT' THEN
        NEW.target_id := NEW.project_id::text;
    END IF;
    IF NEW.target_type = 'PROJECT' AND NEW.target_id <> NEW.project_id::text THEN
        RAISE EXCEPTION 'History project target mismatch';
    ELSIF NEW.target_type = 'SCRIPT' AND NOT EXISTS (
        SELECT 1 FROM script_versions WHERE id::text = NEW.target_id
        AND project_id = NEW.project_id) THEN
        RAISE EXCEPTION 'Review script does not belong to project';
    ELSIF NEW.target_type = 'CLIP' AND NOT EXISTS (
        SELECT 1 FROM clips c JOIN render_jobs r ON r.id = c.render_job_id
        WHERE c.id::text = NEW.target_id AND r.project_id = NEW.project_id) THEN
        RAISE EXCEPTION 'Review Clip does not belong to project';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_history_target BEFORE INSERT ON status_histories
    FOR EACH ROW EXECUTE FUNCTION stemotion_validate_history_target();

CREATE FUNCTION stemotion_reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'status_histories is append-only';
END $$;
CREATE TRIGGER history_append_only BEFORE UPDATE OR DELETE ON status_histories
    FOR EACH ROW EXECUTE FUNCTION stemotion_reject_history_mutation();
CREATE TRIGGER history_no_truncate BEFORE TRUNCATE ON status_histories
    FOR EACH STATEMENT EXECUTE FUNCTION stemotion_reject_history_mutation();

CREATE FUNCTION stemotion_protect_review_target() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM status_histories WHERE target_type = TG_ARGV[0]
        AND target_id = OLD.id::text) THEN
        RAISE EXCEPTION 'Cannot delete an artifact/version referenced by review history';
    END IF;
    RETURN OLD;
END $$;
CREATE TRIGGER protect_reviewed_script BEFORE DELETE ON script_versions
    FOR EACH ROW EXECUTE FUNCTION stemotion_protect_review_target('SCRIPT');
CREATE TRIGGER protect_reviewed_clip BEFORE DELETE ON clips
    FOR EACH ROW EXECUTE FUNCTION stemotion_protect_review_target('CLIP');
