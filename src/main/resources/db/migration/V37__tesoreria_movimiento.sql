-- V37 — Libro de movimientos de tesorería (egresos, recaudos, transferencias)

CREATE TABLE tesoreria_movimiento (
    id                  BIGSERIAL PRIMARY KEY,
    empresa_id          INTEGER        NOT NULL REFERENCES empresa(id),
    cuenta_bancaria_id  BIGINT         NOT NULL REFERENCES cuenta_bancaria(id),
    tipo                VARCHAR(30)    NOT NULL,
        -- EGRESO | RECAUDO | TRANSFERENCIA_SALIDA | TRANSFERENCIA_ENTRADA
    monto               NUMERIC(15,2)  NOT NULL,
    concepto            VARCHAR(500)   NOT NULL,
    beneficiario        VARCHAR(300),
    referencia          VARCHAR(200),
    fecha               DATE           NOT NULL,
    categoria           VARCHAR(100),
        -- Egresos:   PAGO_PROVEEDOR | GASTO_OPERATIVO | NOMINA | IMPUESTO | SERVICIO | OTROS
        -- Recaudos:  VENTA | COBRO_CARTERA | PRESTAMO | DEVOLUCION | OTROS
    transferencia_id    BIGINT,            -- liga SALIDA con ENTRADA (mismo valor)
    conciliado          BOOLEAN        NOT NULL DEFAULT FALSE,
    fecha_conciliacion  DATE,
    anulado             BOOLEAN        NOT NULL DEFAULT FALSE,
    usuario_id          INTEGER        REFERENCES usuario(id),
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tesm_empresa  ON tesoreria_movimiento(empresa_id);
CREATE INDEX idx_tesm_cuenta   ON tesoreria_movimiento(cuenta_bancaria_id);
CREATE INDEX idx_tesm_fecha    ON tesoreria_movimiento(fecha);
CREATE INDEX idx_tesm_tipo     ON tesoreria_movimiento(tipo);
