-- Vincula el pedido_vendedor auto-generado con la venta que lo originó
ALTER TABLE pedido_vendedor ADD COLUMN venta_id BIGINT REFERENCES venta(id);
