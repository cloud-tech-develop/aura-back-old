-- ── V142: de dónde sale (o entra) la plata, declarado explícitamente ────────
--
-- Hasta hoy el sistema DEDUCÍA el origen del dinero a partir del usuario que
-- digitaba el documento: la compra buscaba el turno del comprador, el abono
-- usaba el que mandara el front, y el gasto ni preguntaba — siempre acreditaba
-- CAJA. Como el administrador no tiene turno, sus gastos y abonos no caían en
-- el cierre de ninguna caja, y un gasto pagado por transferencia dejaba la caja
-- contable en negativo con el banco intacto.
--
-- El dinero pertenece a una caja o a una cuenta, nunca a un usuario. Estas
-- columnas son lo que faltaba para que cada documento lo declare y el motor de
-- asientos no tenga que adivinar. Las tres vías son:
--   CAJA            → efectivo; exige turno abierto y mueve el arqueo
--   BANCO           → cuenta_bancaria_id; afecta su cuenta contable y su saldo
--   CUENTA CONTABLE → cuenta elegida a mano, o la de la forma de pago
--
-- Todo es NULL-able o con DEFAULT: los documentos históricos siguen leyéndose
-- igual y el comportamiento previo queda como valor por defecto.

-- ── Gasto ───────────────────────────────────────────────────────────────────
-- Ojo con el nombre: `gasto.cuenta_contable_id` YA EXISTE y significa la cuenta
-- de DÉBITO (a qué gasto se imputa). La nueva es el CRÉDITO — de dónde salió la
-- plata — y por eso se llama `cuenta_pago_id`. Confundirlas invierte el asiento.
ALTER TABLE gasto ADD COLUMN IF NOT EXISTS forma_pago        VARCHAR(20)  NOT NULL DEFAULT 'CONTADO';
ALTER TABLE gasto ADD COLUMN IF NOT EXISTS metodo_pago       VARCHAR(30)  NOT NULL DEFAULT 'EFECTIVO';
ALTER TABLE gasto ADD COLUMN IF NOT EXISTS cuenta_bancaria_id BIGINT;
ALTER TABLE gasto ADD COLUMN IF NOT EXISTS cuenta_pago_id     BIGINT;

-- CONTADO paga ya (caja/banco/cuenta); CREDITO deja una cuenta por pagar.
ALTER TABLE gasto DROP CONSTRAINT IF EXISTS chk_gasto_forma_pago;
ALTER TABLE gasto ADD  CONSTRAINT chk_gasto_forma_pago
    CHECK (forma_pago IN ('CONTADO', 'CREDITO'));

ALTER TABLE gasto DROP CONSTRAINT IF EXISTS fk_gasto_cuenta_bancaria;
ALTER TABLE gasto ADD  CONSTRAINT fk_gasto_cuenta_bancaria
    FOREIGN KEY (cuenta_bancaria_id) REFERENCES cuenta_bancaria(id);

ALTER TABLE gasto DROP CONSTRAINT IF EXISTS fk_gasto_cuenta_pago;
ALTER TABLE gasto ADD  CONSTRAINT fk_gasto_cuenta_pago
    FOREIGN KEY (cuenta_pago_id) REFERENCES plan_cuenta(id);

-- El gasto histórico se registró siempre como pagado de contado en efectivo,
-- que es justo lo que dicen los DEFAULT de arriba. No hay nada que rellenar.

-- ── Pago de compra ──────────────────────────────────────────────────────────
-- Tercera vía para la compra: hoy el pago solo puede apuntar a una cuenta
-- bancaria, así que sin caja abierta el administrador se queda sin salida.
ALTER TABLE compra_pago ADD COLUMN IF NOT EXISTS cuenta_contable_id BIGINT;

ALTER TABLE compra_pago DROP CONSTRAINT IF EXISTS fk_compra_pago_cuenta_contable;
ALTER TABLE compra_pago ADD  CONSTRAINT fk_compra_pago_cuenta_contable
    FOREIGN KEY (cuenta_contable_id) REFERENCES plan_cuenta(id);

-- ── Abonos ──────────────────────────────────────────────────────────────────
-- Mismo motivo en cartera: el administrador cobra o paga sin caja abierta.
ALTER TABLE abonos_cobrar ADD COLUMN IF NOT EXISTS cuenta_contable_id BIGINT;
ALTER TABLE abonos_pagar  ADD COLUMN IF NOT EXISTS cuenta_contable_id BIGINT;

ALTER TABLE abonos_cobrar DROP CONSTRAINT IF EXISTS fk_abonos_cobrar_cuenta_contable;
ALTER TABLE abonos_cobrar ADD  CONSTRAINT fk_abonos_cobrar_cuenta_contable
    FOREIGN KEY (cuenta_contable_id) REFERENCES plan_cuenta(id);

ALTER TABLE abonos_pagar DROP CONSTRAINT IF EXISTS fk_abonos_pagar_cuenta_contable;
ALTER TABLE abonos_pagar ADD  CONSTRAINT fk_abonos_pagar_cuenta_contable
    FOREIGN KEY (cuenta_contable_id) REFERENCES plan_cuenta(id);

-- ── Normalización del método de pago ────────────────────────────────────────
-- El cierre de caja compara `metodo_pago = 'EFECTIVO'` en SQL exacto, pero el
-- método se guardaba tal como lo mandara el front. Un 'efectivo' en minúscula
-- no sumaba al efectivo esperado y el cajero cerraba con un sobrante que no
-- existía. El backend ya normaliza al guardar; esto arregla lo ya guardado.
UPDATE venta_pago    SET metodo_pago = UPPER(TRIM(metodo_pago)) WHERE metodo_pago <> UPPER(TRIM(metodo_pago));
UPDATE compra_pago   SET metodo_pago = UPPER(TRIM(metodo_pago)) WHERE metodo_pago <> UPPER(TRIM(metodo_pago));
UPDATE abonos_cobrar SET metodo_pago = UPPER(TRIM(metodo_pago)) WHERE metodo_pago <> UPPER(TRIM(metodo_pago));
UPDATE abonos_pagar  SET metodo_pago = UPPER(TRIM(metodo_pago)) WHERE metodo_pago <> UPPER(TRIM(metodo_pago));
