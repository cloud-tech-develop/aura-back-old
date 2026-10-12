-- V207 — pago de declaraciones: redondeo a miles, intereses de mora y sanción.
--
-- valor sigue siendo el saldo exacto de las cuentas del impuesto (lo que se
-- cancela en contabilidad). Lo que de verdad sale del banco es
-- valor + ajuste_redondeo + intereses + sancion:
--   ajuste_redondeo: diferencia por aproximar al múltiplo de mil (art. 577 E.T.),
--                    positivo = se paga de más (gasto), negativo = de menos (ingreso).
--   intereses / sancion: los liquida el contribuyente al presentar tarde.

ALTER TABLE declaracion_pago ADD COLUMN IF NOT EXISTS ajuste_redondeo NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE declaracion_pago ADD COLUMN IF NOT EXISTS intereses       NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE declaracion_pago ADD COLUMN IF NOT EXISTS sancion         NUMERIC(18,2) NOT NULL DEFAULT 0;
