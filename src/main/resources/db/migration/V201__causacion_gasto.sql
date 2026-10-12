-- V201 — Causación de gastos y servicios (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §4).
--
-- La factura del proveedor de un servicio (arriendo, honorarios, servicios
-- públicos…) con varias líneas: cada una a su cuenta de gasto y centro de costo,
-- con su IVA y sus retenciones. Genera UNA cuenta por pagar por el neto y un
-- asiento (origen CAUSACION_GASTO). No mueve inventario ni caja.
--
-- No confundir con causacion_programada (E6): esa es la plantilla mensual que
-- propone un asiento en borrador.

CREATE TABLE IF NOT EXISTS causacion_gasto (
    id                   BIGSERIAL      PRIMARY KEY,
    empresa_id           INT            NOT NULL REFERENCES empresa(id),
    sucursal_id          INT            REFERENCES sucursal(id),
    -- Consecutivo interno por empresa (CG-1, CG-2…).
    consecutivo          INT            NOT NULL,
    tercero_id           BIGINT         NOT NULL REFERENCES tercero(id),
    fecha                DATE           NOT NULL,
    fecha_vencimiento    DATE,
    -- FACTURA | DOCUMENTO_SOPORTE | CUENTA_COBRO | OTRO
    tipo_soporte         VARCHAR(20)    NOT NULL DEFAULT 'FACTURA',
    numero_soporte       VARCHAR(50),
    descripcion          VARCHAR(500),
    -- Cuenta por pagar del asiento (2205, 2335…). Sin ella, la de proveedores.
    cuenta_pagar_contable_id BIGINT,
    subtotal             NUMERIC(18,2)  NOT NULL DEFAULT 0,
    iva                  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    retefuente           NUMERIC(18,2)  NOT NULL DEFAULT 0,
    reteiva              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    reteica              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    total_pagar          NUMERIC(18,2)  NOT NULL DEFAULT 0,
    cuenta_pagar_id      BIGINT,
    -- CAUSADA | ANULADA
    estado               VARCHAR(15)    NOT NULL DEFAULT 'CAUSADA',
    usuario_id           INT,
    anulado_por          INT,
    anulado_at           TIMESTAMP,
    motivo_anulacion     VARCHAR(300),
    created_at           TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_causacion_gasto_consecutivo
    ON causacion_gasto (empresa_id, consecutivo);

CREATE INDEX IF NOT EXISTS idx_causacion_gasto_tercero
    ON causacion_gasto (empresa_id, tercero_id, fecha);

CREATE TABLE IF NOT EXISTS causacion_gasto_linea (
    id                   BIGSERIAL      PRIMARY KEY,
    causacion_id         BIGINT         NOT NULL REFERENCES causacion_gasto(id) ON DELETE CASCADE,
    cuenta_id            BIGINT         NOT NULL,
    descripcion          VARCHAR(300),
    centro_costo_id      BIGINT,
    base                 NUMERIC(18,2)  NOT NULL,
    tarifa_iva           NUMERIC(7,4)   NOT NULL DEFAULT 0,
    valor_iva            NUMERIC(18,2)  NOT NULL DEFAULT 0,
    -- false: el IVA no se descuenta y se suma al gasto (mayor valor).
    iva_descontable      BOOLEAN        NOT NULL DEFAULT true,
    tarifa_retencion_id  BIGINT,
    tarifa_retefuente    NUMERIC(7,4)   NOT NULL DEFAULT 0,
    valor_retefuente     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    tarifa_reteiva       NUMERIC(7,4)   NOT NULL DEFAULT 0,
    valor_reteiva        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    -- ReteICA en porcentaje (9,66 por mil = 0,966).
    tarifa_reteica       NUMERIC(7,4)   NOT NULL DEFAULT 0,
    valor_reteica        NUMERIC(18,2)  NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_causacion_gasto_linea_causacion
    ON causacion_gasto_linea (causacion_id);
