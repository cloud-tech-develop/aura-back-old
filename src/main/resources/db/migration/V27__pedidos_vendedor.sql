-- V27: Módulo pedidos de vendedor (ventas de campo)

CREATE TABLE pedido_vendedor (
    id                BIGSERIAL PRIMARY KEY,
    empresa_id        INTEGER NOT NULL REFERENCES empresa(id),
    sucursal_id       INTEGER NOT NULL REFERENCES sucursal(id),
    vendedor_id       INTEGER NOT NULL REFERENCES usuario(id),
    cliente_id        BIGINT  REFERENCES tercero(id),
    numero_pedido     VARCHAR(30),
    estado            VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_DESPACHO',
    subtotal          NUMERIC(14,2) NOT NULL DEFAULT 0,
    descuento_total   NUMERIC(14,2) NOT NULL DEFAULT 0,
    impuesto_total    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total             NUMERIC(14,2) NOT NULL DEFAULT 0,
    observaciones     VARCHAR(500),
    metodo_pago       VARCHAR(30),
    referencia_pago   VARCHAR(100),
    fecha_cobro       TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE pedido_vendedor_detalle (
    id                    BIGSERIAL PRIMARY KEY,
    pedido_vendedor_id    BIGINT NOT NULL REFERENCES pedido_vendedor(id),
    producto_id           BIGINT NOT NULL REFERENCES producto(id),
    cantidad              NUMERIC(12,4) NOT NULL,
    precio_unitario       NUMERIC(14,2) NOT NULL,
    descuento_valor       NUMERIC(14,2) NOT NULL DEFAULT 0,
    impuesto_valor        NUMERIC(14,2) NOT NULL DEFAULT 0,
    subtotal_linea        NUMERIC(14,2) NOT NULL
);

CREATE SEQUENCE pedido_vendedor_seq START WITH 1 INCREMENT BY 1;
