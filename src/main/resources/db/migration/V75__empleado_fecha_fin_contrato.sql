-- ── V75: Empleado — fecha fin de contrato (para contratos a término FIJO) ────

ALTER TABLE empleados
    ADD COLUMN fecha_fin_contrato DATE;
