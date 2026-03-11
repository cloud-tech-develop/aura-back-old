-- ── V19: Módulo de Comisiones ─────────────────────────────────

-- Configuración de comisión por servicio
CREATE TABLE comision_config (
    id                  SERIAL PRIMARY KEY,
    empresa_id          INT NOT NULL REFERENCES empresa(id),
    producto_id         INT NOT NULL REFERENCES producto(id),
    tecnico_id          INT REFERENCES usuario(id),   -- NULL = se asigna en caja
    tipo                VARCHAR(20) NOT NULL DEFAULT 'PORCENTAJE', -- PORCENTAJE | VALOR_FIJO
    porcentaje_tecnico  NUMERIC(5,2) NOT NULL,
    porcentaje_negocio  NUMERIC(5,2) NOT NULL,
    activo              BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT chk_porcentajes CHECK (
        porcentaje_tecnico + porcentaje_negocio = 100
    )
);

CREATE INDEX idx_comision_config_empresa ON comision_config(empresa_id);
CREATE INDEX idx_comision_config_producto ON comision_config(producto_id, empresa_id, activo);

-- Liquidaciones (cuando se le paga al técnico)
CREATE TABLE comision_liquidacion (
    id              SERIAL PRIMARY KEY,
    empresa_id      INT NOT NULL REFERENCES empresa(id),
    tecnico_id      INT NOT NULL REFERENCES usuario(id),
    fecha_desde     DATE NOT NULL,
    fecha_hasta     DATE NOT NULL,
    total_servicios INT NOT NULL DEFAULT 0,
    valor_total     NUMERIC(14,2) NOT NULL DEFAULT 0,
    estado          VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones   TEXT,
    fecha_pago      DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comision_liquidacion_empresa ON comision_liquidacion(empresa_id);
CREATE INDEX idx_comision_liquidacion_tecnico ON comision_liquidacion(tecnico_id, estado);

-- Registro de comisión generada por cada venta de servicio
CREATE TABLE comision_venta (
    id                  SERIAL PRIMARY KEY,
    empresa_id          INT NOT NULL REFERENCES empresa(id),
    venta_id            BIGINT NOT NULL REFERENCES venta(id),
    venta_detalle_id    BIGINT NOT NULL REFERENCES venta_detalle(id),
    producto_id         BIGINT NOT NULL REFERENCES producto(id),
    tecnico_id          INT REFERENCES usuario(id),
    valor_total         NUMERIC(14,2) NOT NULL,
    porcentaje_tecnico  NUMERIC(5,2) NOT NULL,
    porcentaje_negocio  NUMERIC(5,2) NOT NULL,
    valor_tecnico       NUMERIC(14,2) NOT NULL,
    valor_negocio       NUMERIC(14,2) NOT NULL,
    liquidacion_id      INT REFERENCES comision_liquidacion(id),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comision_venta_empresa ON comision_venta(empresa_id);
CREATE INDEX idx_comision_venta_tecnico_pendiente ON comision_venta(tecnico_id, empresa_id, liquidacion_id);
CREATE INDEX idx_comision_venta_liquidacion ON comision_venta(liquidacion_id);
