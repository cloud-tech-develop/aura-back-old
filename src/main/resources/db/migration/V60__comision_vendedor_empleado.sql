-- comision_venta: referencia al empleado (vendedor) para comisiones de tipo VENTA
ALTER TABLE comision_venta
    ADD COLUMN vendedor_id BIGINT REFERENCES empleados(id);

-- comision_liquidacion: referencia al empleado para liquidaciones de tipo VENDEDOR
-- tecnico_id pasa a ser nullable (solo aplica cuando tipo = 'TECNICO')
ALTER TABLE comision_liquidacion
    ADD COLUMN vendedor_id BIGINT REFERENCES empleados(id),
    ALTER COLUMN tecnico_id DROP NOT NULL;
