-- ── V153: "ya se movió de la caja, otro día" en cartera ────────────────────
--
-- V151 resolvió el caso para compra y gasto. En CxC y CxP pasa exactamente lo
-- mismo, y en los dos sentidos:
--
--   · El cliente abonó en efectivo ayer. La plata entró al cajón, el conteo de
--     aquel día la contó y el turno cerró cuadrado contra el cuaderno. Si el
--     abono se registra hoy atado al turno de hoy, el sistema espera un
--     efectivo que nunca entró hoy y el cajero cierra con un FALTANTE.
--
--   · Al proveedor se le pagó en efectivo ayer. Misma historia al revés: el
--     abono registrado hoy le resta al esperado de hoy una plata que hoy no
--     salió, y el cajero cierra con un SOBRANTE.
--
-- El arqueo de un abono no pasa por movimiento_caja como el de una compra:
-- turno_caja_id + metodo_pago EFECTIVO es lo que lo mete en el cierre. Por eso
-- la vía "otro día" guarda el abono SIN turno. Contablemente no cambia nada —
-- el asiento sigue afectando 1105 por el medio de pago.
--
-- La columna existe para poder AUDITARLO: sin ella, un abono sin turno es
-- indistinguible de uno viejo registrado antes de que existiera el turno.  

ALTER TABLE abonos_cobrar ADD COLUMN IF NOT EXISTS caja_otro_dia BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE abonos_pagar  ADD COLUMN IF NOT EXISTS caja_otro_dia BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_abonos_cobrar_caja_otro_diap
    ON abonos_cobrar (fecha_pago) WHERE caja_otro_dia = TRUE;
CREATE INDEX IF NOT EXISTS idx_abonos_pagar_caja_otro_dia
    ON abonos_pagar (fecha_pago) WHERE caja_otro_dia = TRUE;
