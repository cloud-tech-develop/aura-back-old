-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  V45 — Mejoras contables: numero_comprobante + codigo_dian       ║
-- ║  Regla 1: Consecutivo contable visible (CD-000001, RC-000045…)   ║
-- ║  Regla 2: Preparación homologación DIAN en plan de cuentas       ║
-- ╚══════════════════════════════════════════════════════════════════╝

-- Regla 1: Número de comprobante en asiento contable
ALTER TABLE asiento_contable
    ADD COLUMN IF NOT EXISTS numero_comprobante VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_asiento_comprobante
    ON asiento_contable(empresa_id, numero_comprobante);

-- Regla 2: Código DIAN en plan de cuentas
ALTER TABLE plan_cuenta
    ADD COLUMN IF NOT EXISTS codigo_dian VARCHAR(20);
