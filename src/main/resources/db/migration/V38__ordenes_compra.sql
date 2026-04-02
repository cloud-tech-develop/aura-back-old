-- V38 — Órdenes de Compra

CREATE TABLE orden_compra (
    id                      BIGSERIAL PRIMARY KEY,
    empresa_id              INTEGER        NOT NULL REFERENCES empresa(id),
    sucursal_id             INTEGER        NOT NULL REFERENCES sucursal(id),
    proveedor_id            BIGINT         NOT NULL REFERENCES tercero(id),
    usuario_id              INTEGER        REFERENCES usuario(id),
    numero_orden            VARCHAR(20)    NOT NULL,
    estado                  VARCHAR(30)    NOT NULL DEFAULT 'BORRADOR',
        -- BORRADOR | ENVIADA | CONFIRMADA | RECIBIDA_PARCIAL | CERRADA | ANULADA
    fecha                   DATE           NOT NULL,
    fecha_entrega_esperada  DATE,
    observaciones           TEXT,
    total                   NUMERIC(15,2)  NOT NULL DEFAULT 0,
    compra_id               BIGINT         REFERENCES compra(id),
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    UNIQUE(empresa_id, numero_orden)
);

CREATE TABLE orden_compra_detalle (
    id                  BIGSERIAL PRIMARY KEY,
    orden_compra_id     BIGINT         NOT NULL REFERENCES orden_compra(id),
    producto_id         BIGINT         NOT NULL REFERENCES producto(id),
    producto_nombre     VARCHAR(500)   NOT NULL,
    cantidad            NUMERIC(15,3)  NOT NULL,
    cantidad_recibida   NUMERIC(15,3)  NOT NULL DEFAULT 0,
    costo_unitario      NUMERIC(15,2)  NOT NULL,
    subtotal_linea      NUMERIC(15,2)  NOT NULL
);

CREATE INDEX idx_oc_empresa  ON orden_compra(empresa_id);
CREATE INDEX idx_oc_estado   ON orden_compra(estado);
CREATE INDEX idx_ocd_orden   ON orden_compra_detalle(orden_compra_id);
