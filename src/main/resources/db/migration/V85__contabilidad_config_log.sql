-- E1 · Guardarraíles de parametrización contable.
-- Auditoría de cambios del mapeo concepto→cuenta (cuenta_config): quién
-- cambió qué concepto, de qué cuenta a cuál y cuándo. Solo INSERT, nunca
-- UPDATE/DELETE: la historia es inmutable.

CREATE TABLE IF NOT EXISTS contabilidad_config_log (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         INT         NOT NULL,
    concepto           VARCHAR(40) NOT NULL,
    cuenta_anterior_id BIGINT,
    cuenta_nueva_id    BIGINT      NOT NULL,
    usuario_id         BIGINT,
    created_at         TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contabilidad_config_log_empresa
    ON contabilidad_config_log (empresa_id, created_at DESC);
