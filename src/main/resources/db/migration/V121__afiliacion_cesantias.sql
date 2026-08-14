-- ── V121: agregar CESANTIAS como rol y tipo de afiliación ───────────────────
--
-- El fondo de cesantías es otra entidad a la que el empleado está afiliado (y a
-- la que se le paga). Se modela igual que EPS/AFP/CCF/ARL: un tercero con rol
-- CESANTIAS, y una afiliación del contrato de tipo CESANTIAS.

ALTER TABLE tercero_rol DROP CONSTRAINT IF EXISTS chk_tercero_rol;
ALTER TABLE tercero_rol ADD CONSTRAINT chk_tercero_rol CHECK (
    rol IN ('CLIENTE','PROVEEDOR','EMPLEADO','BANCO','EPS','AFP','CCF','ARL','CESANTIAS')
);

ALTER TABLE contrato_afiliacion DROP CONSTRAINT IF EXISTS chk_afil_tipo;
ALTER TABLE contrato_afiliacion ADD CONSTRAINT chk_afil_tipo CHECK (
    tipo IN ('EPS','AFP','CCF','ARL','CESANTIAS')
);
