-- Align the original varchar-based schema with the existing Supabase schema.
-- Keep the existing full_name column and synchronize display_name in the entity.
ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
UPDATE users SET display_name = full_name WHERE display_name IS NULL;
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
