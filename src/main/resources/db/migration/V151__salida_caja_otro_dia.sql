-- ── V151: "ya salió de la caja, otro día" ──────────────────────────────────
--
-- El caso real del cliente: la plata sale del cajón un día, el administrador lo
-- anota en su cuaderno, cuadra la caja física contra ese cuaderno y cierra en
-- cero. Al día siguiente registra la factura en el sistema.
--
-- Para ese documento NO debe crearse ningún movimiento de caja:
--   · La caja de hoy no se toca — la plata no salió de ahí.
--   · La caja de aquel día tampoco — ya se cerró cuadrada contra el conteo
--     físico, y el egreso ya está reflejado en ese conteo.
--
-- Contablemente la plata sí salió de caja, así que el asiento acredita 1105.
-- El motor ya distingue entre "la cuenta contable Caja" y "el arqueo de un
-- turno": son dos cosas distintas y solo la segunda mueve el cierre del cajero.
--
-- Esta columna existe para poder AUDITARLO. Sin ella, la vía no deja rastro
-- visible: alguien podría registrar salidas de caja de días pasados y nadie las
-- vería. No frena nada — el panel de supervisión las lista.

ALTER TABLE compra ADD COLUMN IF NOT EXISTS salida_caja_otro_dia BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE gasto  ADD COLUMN IF NOT EXISTS salida_caja_otro_dia BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_compra_salida_otro_dia
    ON compra (empresa_id, fecha) WHERE salida_caja_otro_dia = TRUE;
CREATE INDEX IF NOT EXISTS idx_gasto_salida_otro_dia
    ON gasto (empresa_id, fecha) WHERE salida_caja_otro_dia = TRUE;
