-- Additive bridge for both the original Flyway schema and shared Supabase.
-- order_no is the API's canonical ordering. Preserve the older column for
-- consumers of the shared schema, without assigning every scene one default.
ALTER TABLE scenes ADD COLUMN IF NOT EXISTS order_index INTEGER;
UPDATE scenes SET order_index = order_no WHERE order_index IS DISTINCT FROM order_no;
ALTER TABLE scenes ALTER COLUMN order_index SET NOT NULL;
