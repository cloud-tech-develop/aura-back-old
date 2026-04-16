-- V58: Agrega campo iva_incluido a la tabla producto
-- Indica si el precio de venta ya tiene el IVA incluido (true)
-- o si el IVA se suma encima del precio base (false, comportamiento por defecto)

ALTER TABLE producto
    ADD COLUMN IF NOT EXISTS iva_incluido BOOLEAN NOT NULL DEFAULT FALSE;
