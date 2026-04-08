-- ─── Agregar ciudad, ciudad_id y barrio a locales ─────────────────────────
ALTER TABLE locales
    ADD COLUMN IF NOT EXISTS ciudad VARCHAR(200),
    ADD COLUMN IF NOT EXISTS ciudad_id INT,
    ADD COLUMN IF NOT EXISTS barrio VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_locales_ciudad ON locales(ciudad_id);
