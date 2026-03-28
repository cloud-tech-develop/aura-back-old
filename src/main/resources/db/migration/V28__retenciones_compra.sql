-- V28: Retenciones en compras (retefuente, reteiva, reteica)
ALTER TABLE compra
    ADD COLUMN IF NOT EXISTS retefuente_pct    NUMERIC(5,2)  DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retefuente_valor  NUMERIC(15,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reteiva_pct       NUMERIC(5,2)  DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reteiva_valor     NUMERIC(15,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reteica_pct       NUMERIC(5,2)  DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reteica_valor     NUMERIC(15,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_retenciones NUMERIC(15,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS neto_a_pagar      NUMERIC(15,2);
