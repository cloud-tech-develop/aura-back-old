-- V196 — Factura AIU para construcción (FV5 de docs/PLAN_FACTURACION.md).
--
-- En un contrato AIU las líneas de la obra son el costo directo (sin IVA) y
-- encima van Administración, Imprevistos y Utilidad como porcentajes de ese
-- costo; el IVA se liquida solo sobre la Utilidad. La factura guarda los
-- porcentajes y el servicio genera las tres líneas (aiu_tipo) al guardar.

ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS aiu BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS aiu_administracion_pct NUMERIC(6,2) NOT NULL DEFAULT 0;
ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS aiu_imprevistos_pct NUMERIC(6,2) NOT NULL DEFAULT 0;
ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS aiu_utilidad_pct NUMERIC(6,2) NOT NULL DEFAULT 0;
ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS aiu_iva_pct NUMERIC(6,2) NOT NULL DEFAULT 19;

-- ADMINISTRACION | IMPREVISTOS | UTILIDAD en las líneas que genera el AIU; NULL en las de la obra.
ALTER TABLE factura_venta_detalle ADD COLUMN IF NOT EXISTS aiu_tipo VARCHAR(15);
