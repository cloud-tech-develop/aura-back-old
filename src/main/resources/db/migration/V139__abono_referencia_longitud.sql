-- ── V139: `referencia` de los abonos pasa de VARCHAR(100) a VARCHAR(255) ────
--
-- Problema que resuelve: al registrar un movimiento de caja contra una cuenta
-- por cobrar/pagar (TurnoCajaServiceImpl.registrarMovimiento) el `concepto` del
-- movimiento — VARCHAR(255) y texto libre del cajero — se guarda tal cual en
-- `abonos_cobrar.referencia`, que solo admitía 100. Un concepto largo hacía
-- estallar el INSERT con "value too long for type character varying(100)".
--
-- Se iguala el destino al origen en las dos tablas de abonos (el mismo camino
-- existe para el EGRESO contra cuentas por pagar).

ALTER TABLE abonos_cobrar
    ALTER COLUMN referencia TYPE VARCHAR(255);

ALTER TABLE abonos_pagar
    ALTER COLUMN referencia TYPE VARCHAR(255);

COMMENT ON COLUMN abonos_cobrar.referencia IS
    'Texto libre del recaudo (nº de consignación, concepto del movimiento de caja).';
COMMENT ON COLUMN abonos_pagar.referencia IS
    'Texto libre del pago (nº de transferencia, concepto del movimiento de caja).';
