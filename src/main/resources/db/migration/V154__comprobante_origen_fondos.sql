-- ── V154: el comprobante manual declara de dónde sale la plata ────────────
--
-- Un CE o un RC hechos desde contabilidad movían dinero real sin dejar rastro
-- en ningún arqueo. El cruce de cartera creaba el abono SIN turno_caja_id y con
-- metodo_pago = 'COMPROBANTE' (un valor inventado que MediosPago.esEfectivo()
-- no reconoce), y el cierre de caja arma su detalle con tres consultas, todas
-- por turno_caja_id. Resultado: el comprobante manual era la única vía por la
-- que entraba o salía efectivo sin aparecer en el cierre de nadie.
--
-- No era un campo olvidado: el formulario nunca preguntaba de dónde salía la
-- plata. Pedía una cuenta contable 11xx, y una cuenta contable no distingue el
-- cajón de la sucursal 2 de la cuenta de Bancolombia.
--
-- Estas columnas guardan la declaración — la misma que ya hacen compra, gasto y
-- abono a través de OrigenFondosService (V142, V145–V151, V153). Se guarda lo
-- declarado, no lo deducido: es lo que permite reconstruir meses después por
-- qué un CE cayó en una caja y no en otra.

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS turno_caja_id BIGINT;
ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS metodo_pago VARCHAR(30);
ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS cuenta_bancaria_id BIGINT;
ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS caja_otro_dia BOOLEAN NOT NULL DEFAULT FALSE;

-- El comprobante navega hacia su turno; el turno no navega hacia sus
-- comprobantes (el cierre los ve a través de los abonos y de movimiento_caja).
-- Por eso el índice va en un solo sentido.
CREATE INDEX IF NOT EXISTS idx_asiento_contable_turno_caja
    ON asiento_contable (turno_caja_id) WHERE turno_caja_id IS NOT NULL;

-- La búsqueda de los movimientos que dejó un comprobante (la hace su anulación)
-- ya está cubierta: V145 creó idx_mov_caja_origen sobre
-- movimiento_caja (origen_tipo, origen_id).
