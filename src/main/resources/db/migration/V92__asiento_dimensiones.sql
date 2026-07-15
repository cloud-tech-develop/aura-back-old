-- E7 · Dimensiones contables proyecto/frente (C5): habilita rentabilidad
-- por obra. Columnas explícitas (no tabla genérica de dimensiones — sería
-- sobre-ingeniería a este tamaño).

ALTER TABLE asiento_detalle
    ADD COLUMN IF NOT EXISTS proyecto_id BIGINT,
    ADD COLUMN IF NOT EXISTS frente_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_asiento_detalle_proyecto
    ON asiento_detalle (proyecto_id) WHERE proyecto_id IS NOT NULL;

-- Compras y gastos pueden imputarse a un proyecto/frente desde el form.
ALTER TABLE compra
    ADD COLUMN IF NOT EXISTS proyecto_id BIGINT,
    ADD COLUMN IF NOT EXISTS frente_id BIGINT;

ALTER TABLE gasto
    ADD COLUMN IF NOT EXISTS proyecto_id BIGINT,
    ADD COLUMN IF NOT EXISTS frente_id BIGINT;

-- La venta hereda el centro de costo de su sucursal (parametrizable).
ALTER TABLE sucursal
    ADD COLUMN IF NOT EXISTS centro_costo_id BIGINT;
