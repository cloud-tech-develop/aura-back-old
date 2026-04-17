-- Distinguir si una comisión fue generada por servicio (técnico) o por venta (vendedor)
ALTER TABLE comision_venta
    ADD COLUMN modalidad VARCHAR(20) NOT NULL DEFAULT 'SERVICIO';

-- Distinguir el tipo de liquidación: TECNICO (servicios) o VENDEDOR (ventas)
ALTER TABLE comision_liquidacion
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'TECNICO';
