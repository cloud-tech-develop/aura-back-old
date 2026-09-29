-- V65: contabilización del movimiento de caja
-- Se agrega el concepto de caja elegido (su contrapartida) y el método de pago,
-- para que el movimiento manual genere su asiento (DB/CR caja + cuenta del concepto).

ALTER TABLE movimiento_caja
    ADD COLUMN IF NOT EXISTS concepto_caja_id BIGINT,
    ADD COLUMN IF NOT EXISTS metodo_pago      VARCHAR(30);
