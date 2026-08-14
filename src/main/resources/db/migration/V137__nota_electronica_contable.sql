-- F5: base gravable e IVA de la nota, para contabilizarla (reversa de ingreso).
-- Se calculan al persistir a partir de los items enviados a Factus.
ALTER TABLE nota_electronica ADD COLUMN IF NOT EXISTS base_gravable NUMERIC(15,2);
ALTER TABLE nota_electronica ADD COLUMN IF NOT EXISTS iva NUMERIC(15,2);
