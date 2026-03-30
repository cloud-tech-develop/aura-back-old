-- V36 — Módulo Tesorería: Cuentas Bancarias

CREATE TABLE cuenta_bancaria (
    id              BIGSERIAL PRIMARY KEY,
    empresa_id      INTEGER       NOT NULL REFERENCES empresa(id),
    nombre          VARCHAR(200)  NOT NULL,
    tipo            VARCHAR(30)   NOT NULL,
        -- BANCO | CAJA | NEQUI | DAVIPLATA | OTROS
    banco           VARCHAR(200),
    numero_cuenta   VARCHAR(100),
    titular         VARCHAR(300),
    saldo_inicial   NUMERIC(15,2) NOT NULL DEFAULT 0,
    saldo_actual    NUMERIC(15,2) NOT NULL DEFAULT 0,
    activa          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cuenta_bancaria_empresa ON cuenta_bancaria(empresa_id);
