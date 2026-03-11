-- Permite asignar precio a un producto directamente, sin necesitar presentación
ALTER TABLE producto_precio
    ADD COLUMN producto_id BIGINT REFERENCES producto(id),
    ALTER COLUMN producto_presentacion_id DROP NOT NULL;

-- Al menos uno de los dos debe estar presente
ALTER TABLE producto_precio
    ADD CONSTRAINT chk_precio_tiene_producto
    CHECK (producto_presentacion_id IS NOT NULL OR producto_id IS NOT NULL);
