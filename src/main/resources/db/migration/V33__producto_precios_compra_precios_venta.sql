-- Agregar precio_2 y precio_3 al catálogo de productos
ALTER TABLE producto
    ADD COLUMN IF NOT EXISTS precio_2 NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS precio_3 NUMERIC(15, 2);

-- Agregar precios de venta al detalle de compra
ALTER TABLE compra_detalle
    ADD COLUMN IF NOT EXISTS precio_venta1 NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS precio_venta2 NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS precio_venta3 NUMERIC(15, 2);
