-- V64: Catálogo de Conceptos de caja
-- Conceptos amigables para que el cajero registre ingresos/egresos sin ver cuentas.
-- Cada concepto mapea a una cuenta contable (la contrapartida del asiento que se
-- generará en la Fase 2). El contador/admin los configura una sola vez.

CREATE TABLE IF NOT EXISTS concepto_caja (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         INTEGER      NOT NULL,
    nombre             VARCHAR(120) NOT NULL,
    -- INGRESO | EGRESO
    tipo               VARCHAR(10)  NOT NULL,
    -- Cuenta contable de contrapartida (gasto, ingreso, CxC, CxP, etc.)
    cuenta_contable_id BIGINT       NOT NULL,
    activo             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_concepto_caja_empresa_tipo
    ON concepto_caja (empresa_id, tipo, activo);
