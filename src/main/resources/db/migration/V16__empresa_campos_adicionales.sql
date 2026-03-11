-- ─── Campos adicionales en empresa ───────────────────────────
ALTER TABLE empresa
    ADD COLUMN IF NOT EXISTS telefono     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS municipio    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS municipio_id INT;
