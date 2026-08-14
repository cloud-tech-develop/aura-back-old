-- ── V122: agrupar prestaciones en un "lote" ────────────────────────────────
--
-- Una liquidación definitiva genera varias filas (cesantías, intereses, prima,
-- vacaciones, indemnización). El `lote` las agrupa: el listado muestra una sola
-- fila con el total, y el detalle abre el desglose. Una liquidación individual
-- es un lote de una sola fila.

ALTER TABLE liquidacion_prestacion
    ADD COLUMN IF NOT EXISTS lote VARCHAR(48);

-- Backfill: cada fila existente queda como su propio lote.
UPDATE liquidacion_prestacion SET lote = 'P-' || id WHERE lote IS NULL;

CREATE INDEX IF NOT EXISTS idx_liq_prest_lote ON liquidacion_prestacion(lote);
