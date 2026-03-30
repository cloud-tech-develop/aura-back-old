-- V41: Comisiones de Venta — por producto o categoría

ALTER TABLE comision_config
    ADD COLUMN IF NOT EXISTS modalidad    VARCHAR(20) NOT NULL DEFAULT 'SERVICIO',
    ADD COLUMN IF NOT EXISTS categoria_id INT         REFERENCES categoria(id),
    ALTER COLUMN producto_id DROP NOT NULL;

-- Eliminar constraint original (sumaba 100 para todos)
ALTER TABLE comision_config DROP CONSTRAINT IF EXISTS chk_porcentajes;

-- Solo SERVICIO exige que los porcentajes sumen 100
ALTER TABLE comision_config
    ADD CONSTRAINT chk_porcentajes_servicio
    CHECK (modalidad != 'SERVICIO' OR (porcentaje_tecnico + porcentaje_negocio = 100));

-- Debe haber objetivo: producto o categoría
ALTER TABLE comision_config
    ADD CONSTRAINT chk_objetivo_comision
    CHECK (producto_id IS NOT NULL OR categoria_id IS NOT NULL);

-- Índices para búsqueda de comisiones VENTA
CREATE INDEX IF NOT EXISTS idx_comision_config_categoria
    ON comision_config(categoria_id, empresa_id, activo);

CREATE INDEX IF NOT EXISTS idx_comision_config_modalidad
    ON comision_config(modalidad, empresa_id, activo);
