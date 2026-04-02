CREATE TABLE devolucion (
    id BIGSERIAL PRIMARY KEY,
    empresa_id INTEGER NOT NULL REFERENCES empresa(id),
    sucursal_id INTEGER NOT NULL REFERENCES sucursal(id),
    venta_id BIGINT NOT NULL REFERENCES venta(id),
    cliente_id BIGINT REFERENCES tercero(id),
    usuario_id INTEGER NOT NULL REFERENCES usuario(id),
    consecutivo BIGINT,
    tipo VARCHAR(20) NOT NULL DEFAULT 'PARCIAL',
    estado VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA',
    motivo VARCHAR(500),
    total_devolucion DECIMAL(15,2) NOT NULL DEFAULT 0,
    reintegra_inventario BOOLEAN NOT NULL DEFAULT TRUE,
    observaciones VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE devolucion_detalle (
    id BIGSERIAL PRIMARY KEY,
    devolucion_id BIGINT NOT NULL REFERENCES devolucion(id),
    producto_id BIGINT NOT NULL REFERENCES producto(id),
    producto_presentacion_id BIGINT REFERENCES producto_presentacion(id),
    lote_id BIGINT REFERENCES lote(id),
    cantidad DECIMAL(15,4) NOT NULL,
    precio_unitario DECIMAL(15,2) NOT NULL DEFAULT 0,
    impuesto_valor DECIMAL(15,2) NOT NULL DEFAULT 0,
    subtotal_linea DECIMAL(15,2) NOT NULL DEFAULT 0
);

ALTER TABLE venta ADD COLUMN IF NOT EXISTS estado_devolucion VARCHAR(20) NULL;
