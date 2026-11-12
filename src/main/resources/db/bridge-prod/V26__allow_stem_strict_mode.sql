-- Requires Lê review; applied only with V23–V25 in R3/R5
SET LOCAL search_path = pg_catalog;

ALTER TABLE public.stem_settings DROP CONSTRAINT stem_settings_mode_check;
ALTER TABLE public.stem_settings ADD CONSTRAINT stem_settings_mode_check CHECK (mode IN ('OFF', 'ADVISORY', 'STRICT'));
