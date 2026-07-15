-- E2 · Pieza 2 de tesorería: formas de pago parametrizables.
-- Cada método de pago (EFECTIVO, TRANSFERENCIA, NEQUI…) puede mapearse a una
-- cuenta contable del disponible (11xx). El motor resuelve:
-- cuenta bancaria → forma de pago → fallback CAJA/BANCOS.

CREATE TABLE IF NOT EXISTS forma_pago_contable (
    id                       BIGSERIAL PRIMARY KEY,
    empresa_id               INT          NOT NULL,
    codigo                   VARCHAR(40)  NOT NULL,
    nombre                   VARCHAR(80)  NOT NULL,
    cuenta_contable_id       BIGINT,
    requiere_cuenta_bancaria BOOLEAN      NOT NULL DEFAULT FALSE,
    activo                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, codigo)
);

CREATE INDEX IF NOT EXISTS idx_forma_pago_contable_empresa
    ON forma_pago_contable (empresa_id, activo);
