-- ── V162: presentaciones en compra, merma y obsequio; venta por unidad ─────
--
-- Compras, mermas y obsequios se pueden escribir en una presentación ("4 Pacas
-- a $52.500"). `cantidad` y `costo_unitario` del detalle SIGUEN en unidad base
-- (100 und a $2.100): inventario, kardex, anular, editar, nota crédito,
-- contabilidad y reportes no cambian. Lo que escribió el usuario se guarda
-- aparte, para mostrarlo y para volver a editar la compra tal como se hizo.
--
-- producto.vende_por_unidad = false → en el POS solo se ofrecen sus
-- presentaciones (la paca), nunca la unidad suelta.

ALTER TABLE producto
    ADD COLUMN IF NOT EXISTS vende_por_unidad BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN producto.vende_por_unidad IS
    'false = el POS solo ofrece sus presentaciones; la unidad suelta no se vende.';

ALTER TABLE compra_detalle
    ADD COLUMN IF NOT EXISTS producto_presentacion_id BIGINT REFERENCES producto_presentacion (id),
    ADD COLUMN IF NOT EXISTS cantidad_presentacion    NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS costo_presentacion       NUMERIC(18,2);

COMMENT ON COLUMN compra_detalle.cantidad_presentacion IS
    'Cantidad escrita en la presentación (4 pacas). La cantidad en unidad base está en cantidad.';

ALTER TABLE merma_detalle
    ADD COLUMN IF NOT EXISTS producto_presentacion_id BIGINT REFERENCES producto_presentacion (id),
    ADD COLUMN IF NOT EXISTS cantidad_presentacion    NUMERIC(18,6);

ALTER TABLE obsequio_detalle
    ADD COLUMN IF NOT EXISTS producto_presentacion_id BIGINT REFERENCES producto_presentacion (id),
    ADD COLUMN IF NOT EXISTS cantidad_presentacion    NUMERIC(18,6);
