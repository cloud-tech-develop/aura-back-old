-- V35 — Tabla de pagos por compra (métodos: EFECTIVO, TARJETA, TRANSFERENCIA, etc.)

CREATE TABLE compra_pago (
    id          BIGSERIAL PRIMARY KEY,
    compra_id   BIGINT        NOT NULL REFERENCES compra(id),
    usuario_id  INTEGER       REFERENCES usuario(id),
    metodo_pago VARCHAR(30)   NOT NULL,
        -- EFECTIVO | TARJETA | TRANSFERENCIA | CHEQUE | CREDITO
    monto       NUMERIC(15,2) NOT NULL,
    banco       VARCHAR(300),
    fecha_pago  TIMESTAMP     NOT NULL DEFAULT NOW(),
    activo      BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_compra_pago_compra ON compra_pago(compra_id);
