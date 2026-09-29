-- V179 — De qué cuenta salió el pago en efectivo de una nómina.
--
-- El pago de nómina asumía EFECTIVO cuando no llegaba el medio de pago y
-- acreditaba la caja sin preguntar de cuál. Ahora pasa por el origen de fondos
-- (caja abierta o cuenta de fondos como la caja menor) y guarda la cuenta
-- resuelta, que es el crédito del asiento de pago.

ALTER TABLE nomina ADD COLUMN IF NOT EXISTS cuenta_pago_id BIGINT;
