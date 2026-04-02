CREATE TABLE IF NOT EXISTS comprobante_caja (
    id                  BIGSERIAL PRIMARY KEY,
    empresa_id          INTEGER NOT NULL,
    numero_comprobante  VARCHAR(20) NOT NULL UNIQUE,
    tipo                VARCHAR(10) NOT NULL,       -- INGRESO | EGRESO
    concepto            VARCHAR(500) NOT NULL,
    monto               NUMERIC(15,2) NOT NULL,
    metodo_pago         VARCHAR(30),               -- EFECTIVO | TRANSFERENCIA | null (CxC/CxP)
    entregado_a         VARCHAR(200),               -- opcional: nombre quien recibe/entrega
    origen              VARCHAR(30),               -- MANUAL | DEVOLUCION | ABONO_CXC | ABONO_CXP
    origen_id           BIGINT,
    turno_caja_id       BIGINT,
    usuario_id          INTEGER,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comprobante_caja_empresa ON comprobante_caja(empresa_id);
CREATE INDEX IF NOT EXISTS idx_comprobante_caja_tipo    ON comprobante_caja(empresa_id, tipo);
CREATE INDEX IF NOT EXISTS idx_comprobante_caja_turno   ON comprobante_caja(turno_caja_id);
