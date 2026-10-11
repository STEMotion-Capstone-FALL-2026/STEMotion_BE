-- Additive metadata only. Existing scene/template API fields remain unchanged.
ALTER TABLE remotion_templates ADD COLUMN subject VARCHAR(32);
ALTER TABLE remotion_templates ADD COLUMN asset_pack VARCHAR(128);
ALTER TABLE remotion_templates ADD COLUMN slots JSONB;
ALTER TABLE remotion_templates ADD CONSTRAINT ck_template_slots_object
    CHECK (slots IS NULL OR jsonb_typeof(slots) = 'object');
