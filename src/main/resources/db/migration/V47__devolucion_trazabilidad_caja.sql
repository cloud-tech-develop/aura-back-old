-- V47: Trazabilidad de movimientos de caja y tesorería en devoluciones
ALTER TABLE devolucion
    ADD COLUMN IF NOT EXISTS movimiento_caja_id      BIGINT,
    ADD COLUMN IF NOT EXISTS tesoreria_movimiento_id BIGINT;

COMMENT ON COLUMN devolucion.movimiento_caja_id      IS 'FK al movimiento_caja generado al devolver en efectivo';
COMMENT ON COLUMN devolucion.tesoreria_movimiento_id IS 'FK al tesoreria_movimiento generado al devolver dinero';
