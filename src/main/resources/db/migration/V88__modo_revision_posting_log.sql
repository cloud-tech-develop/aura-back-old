-- E3 · Estados de comprobante y modo revisión del contador.
-- AUTOMATICO (default): todo asiento nace CONTABILIZADO (igual que hoy).
-- REVISION: los asientos automáticos nacen BORRADOR y el contador los
-- aprueba; BORRADOR no suma en reportes oficiales.
ALTER TABLE empresa
    ADD COLUMN IF NOT EXISTS modo_contabilizacion VARCHAR(15) NOT NULL DEFAULT 'AUTOMATICO';

-- Vista positiva de auditoría del posting automático: qué se contabilizó,
-- desde dónde y qué falló. Solo INSERT.
CREATE TABLE IF NOT EXISTS contabilidad_posting_log (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  INT          NOT NULL,
    tipo_origen VARCHAR(30)  NOT NULL,
    origen_id   BIGINT       NOT NULL,
    asiento_id  BIGINT,
    estado      VARCHAR(10)  NOT NULL,          -- EXITO | ERROR
    error       VARCHAR(500),
    usuario_id  BIGINT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contab_posting_log_empresa
    ON contabilidad_posting_log (empresa_id, created_at DESC);
