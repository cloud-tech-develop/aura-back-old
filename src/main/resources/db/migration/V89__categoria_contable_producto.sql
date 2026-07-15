-- E4 · Categorías contables de producto (C2): bebidas gravadas, servicios
-- y activos contabilizan a cuentas distintas sin que el cajero vea cuentas.
-- Jerarquía de resolución: override del producto → categoría → concepto empresa.

CREATE TABLE IF NOT EXISTS categoria_contable_producto (
    id                   BIGSERIAL PRIMARY KEY,
    empresa_id           INT         NOT NULL,
    nombre               VARCHAR(80) NOT NULL,
    tipo                 VARCHAR(20) NOT NULL DEFAULT 'BIEN',  -- BIEN|SERVICIO|INSUMO|ACTIVO_FIJO
    cuenta_ingreso_id    BIGINT,
    cuenta_inventario_id BIGINT,
    cuenta_costo_id      BIGINT,
    cuenta_devolucion_id BIGINT,
    impuesto_id          BIGINT,      -- FK a impuesto (E5), aún sin usar
    activo               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, nombre)
);

CREATE INDEX IF NOT EXISTS idx_categoria_contable_empresa
    ON categoria_contable_producto (empresa_id, activo);

-- Producto: categoría + overrides excepcionales (normalmente NULL).
ALTER TABLE producto
    ADD COLUMN IF NOT EXISTS categoria_contable_id BIGINT,
    ADD COLUMN IF NOT EXISTS cuenta_ingreso_id BIGINT,
    ADD COLUMN IF NOT EXISTS cuenta_costo_id BIGINT,
    ADD COLUMN IF NOT EXISTS cuenta_inventario_id BIGINT;
