-- V208 — gasto: concepto de retención (como la compra en V203).
--
-- El concepto elegido (tarifa de retención) da la cuenta del asiento
-- (honorarios → 236515, servicios → 236525…) y el acreedor de la declaración.
-- Los gastos anteriores quedan sin concepto: su retención sigue en la cuenta
-- por defecto.

ALTER TABLE gasto ADD COLUMN IF NOT EXISTS retefuente_tarifa_id BIGINT REFERENCES tarifa_retencion(id);
ALTER TABLE gasto ADD COLUMN IF NOT EXISTS reteica_tarifa_id    BIGINT REFERENCES tarifa_retencion(id);
