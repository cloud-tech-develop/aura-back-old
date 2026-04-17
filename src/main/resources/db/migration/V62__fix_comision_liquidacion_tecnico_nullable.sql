-- tecnico_id debe ser nullable porque la liquidación puede ser de tipo VENDEDOR
ALTER TABLE comision_liquidacion ALTER COLUMN tecnico_id DROP NOT NULL;
