-- TEST ONLY. Follows V91000 in test environments.
ALTER TABLE stem_settings DROP CONSTRAINT stem_settings_mode_check;
ALTER TABLE stem_settings ADD CONSTRAINT stem_settings_mode_check CHECK (mode IN ('OFF', 'ADVISORY', 'STRICT'));
