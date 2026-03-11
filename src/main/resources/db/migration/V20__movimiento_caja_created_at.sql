-- Agregar columnas faltantes a movimiento_caja
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS concepto VARCHAR(255);
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS monto DECIMAL(15, 2);
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS tipo VARCHAR(20);
