-- =====================================================
-- HU-014: Sistema de Precios Dinámicos por Cliente
-- =====================================================

-- Tabla para precios especiales por cliente
CREATE TABLE IF NOT EXISTS precio_cliente (
    id BIGSERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresa(id),
    tercero_id INT NOT NULL REFERENCES tercero(id),
    producto_presentacion_id INT NOT NULL REFERENCES producto_presentacion(id),
    precio_especial DECIMAL(19,2) NOT NULL,
    fecha_inicio TIMESTAMP DEFAULT NOW(),
    fecha_fin TIMESTAMP,
    activo BOOLEAN DEFAULT TRUE,
    observaciones VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- Tabla para descuentos por cliente
CREATE TABLE IF NOT EXISTS descuento_cliente (
    id BIGSERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresa(id),
    tercero_id INT NOT NULL REFERENCES tercero(id),
    categoria_id INT REFERENCES categoria(id),
    porcentaje_descuento DECIMAL(5,2) NOT NULL,
    tipo_descuento VARCHAR(20) DEFAULT 'porcentaje',
    fecha_inicio TIMESTAMP DEFAULT NOW(),
    fecha_fin TIMESTAMP,
    activo BOOLEAN DEFAULT TRUE,
    observaciones VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- Tabla para precios por volumen
CREATE TABLE IF NOT EXISTS precio_volumen (
    id BIGSERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresa(id),
    producto_presentacion_id INT NOT NULL REFERENCES producto_presentacion(id),
    cantidad_minima INT,
    cantidad_maxima INT,
    precio_unitario DECIMAL(19,2) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    observaciones VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- =====================================================
-- ÍNDICES PARA MEJORAR PERFORMANCE
-- =====================================================

-- Índices para precio_cliente
CREATE INDEX IF NOT EXISTS idx_precio_cliente_empresa ON precio_cliente(empresa_id);
CREATE INDEX IF NOT EXISTS idx_precio_cliente_tercero ON precio_cliente(tercero_id);
CREATE INDEX IF NOT EXISTS idx_precio_cliente_producto ON precio_cliente(producto_presentacion_id);
CREATE INDEX IF NOT EXISTS idx_precio_cliente_activo ON precio_cliente(activo) WHERE activo = TRUE;
CREATE INDEX IF NOT EXISTS idx_precio_cliente_vigencia ON precio_cliente(fecha_inicio, fecha_fin) WHERE activo = TRUE;

-- Índices para descuento_cliente
CREATE INDEX IF NOT EXISTS idx_descuento_cliente_empresa ON descuento_cliente(empresa_id);
CREATE INDEX IF NOT EXISTS idx_descuento_cliente_tercero ON descuento_cliente(tercero_id);
CREATE INDEX IF NOT EXISTS idx_descuento_cliente_categoria ON descuento_cliente(categoria_id);
CREATE INDEX IF NOT EXISTS idx_descuento_cliente_activo ON descuento_cliente(activo) WHERE activo = TRUE;
CREATE INDEX IF NOT EXISTS idx_descuento_cliente_vigencia ON descuento_cliente(fecha_inicio, fecha_fin) WHERE activo = TRUE;

-- Índices para precio_volumen
CREATE INDEX IF NOT EXISTS idx_precio_volumen_empresa ON precio_volumen(empresa_id);
CREATE INDEX IF NOT EXISTS idx_precio_volumen_producto ON precio_volumen(producto_presentacion_id);
CREATE INDEX IF NOT EXISTS idx_precio_volumen_activo ON precio_volumen(activo) WHERE activo = TRUE;
CREATE INDEX IF NOT EXISTS idx_precio_volumen_rango ON precio_volumen(cantidad_minima, cantidad_maxima);

-- =====================================================
-- COMENTARIOS PARA DOCUMENTACIÓN
-- =====================================================

COMMENT ON TABLE precio_cliente IS 'Precios especiales asignados a clientes específicos para productos';
COMMENT ON TABLE descuento_cliente IS 'Descuentos por cliente sobre categorías de productos';
COMMENT ON TABLE precio_volumen IS 'Precios por volumen según cantidad de productos comprados';

COMMENT ON COLUMN precio_cliente.precio_especial IS 'Precio especial para el cliente (Precio 1)';
COMMENT ON COLUMN descuento_cliente.porcentaje_descuento IS 'Porcentaje de descuento (Precio 2)';
COMMENT ON COLUMN precio_volumen.precio_unitario IS 'Precio unitario según rango de volumen (Precio 3)';

-- =====================================================
-- DATOS DE EJEMPLO (OPCIONAL)
-- =====================================================

-- INSERT INTO lista_precios (empresa_id, nombre, activa) VALUES (1, 'Precio Público', true);
-- INSERT INTO lista_precios (empresa_id, nombre, activa) VALUES (1, 'Precio Mayorista', true);
-- INSERT INTO lista_precios (empresa_id, nombre, activa) VALUES (1, 'Precio Cliente Frecuente', true);
