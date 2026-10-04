-- V197 — Facturación: facturar desde cotización o pedido, y centro de costo
-- (FV1/FV4 de docs/PLAN_FACTURACION.md).
--
-- - factura_venta.cotizacion_id / pedido_vendedor_id: de qué documento sale.
--   Al emitir, la venta lleva el mismo origen: la cotización queda PARCIAL o
--   CONVERTIDA (D1) y el pedido queda enlazado y despachado.
-- - factura_venta_detalle.cotizacion_detalle_id: la línea cotizada que factura.
-- - centro_costo_id en la factura y en la venta: si se elige, manda sobre el
--   de la sucursal en el asiento.

ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS cotizacion_id BIGINT;
ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS pedido_vendedor_id BIGINT;
ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS centro_costo_id BIGINT;
ALTER TABLE factura_venta_detalle ADD COLUMN IF NOT EXISTS cotizacion_detalle_id BIGINT;
ALTER TABLE venta ADD COLUMN IF NOT EXISTS centro_costo_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_factura_venta_cotizacion ON factura_venta (cotizacion_id) WHERE cotizacion_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_factura_venta_pedido ON factura_venta (pedido_vendedor_id) WHERE pedido_vendedor_id IS NOT NULL;
