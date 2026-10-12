-- V204 — A quién se le paga cada impuesto, y el pago de la declaración.
--
-- Cada impuesto (IVA, INC) y cada tarifa de retención tiene su tercero
-- acreedor: la DIAN para el IVA, el INC, la retefuente y el reteIVA; la
-- secretaría de hacienda del municipio para el reteICA. Las líneas del asiento
-- de cada factura siguen a nombre del cliente o proveedor (certificados y
-- exógena); el acreedor se usa en la declaración y en su pago.
--
-- El pago de la declaración cierra el saldo del período de las cuentas del
-- impuesto (2408, 135517, 2365, 2367, 2368) contra el banco o la caja, a
-- nombre del acreedor.

ALTER TABLE impuesto ADD COLUMN IF NOT EXISTS tercero_acreedor_id BIGINT;
ALTER TABLE tarifa_retencion ADD COLUMN IF NOT EXISTS tercero_acreedor_id BIGINT;

-- La DIAN como tercero de cada empresa (si no la tiene ya, viva o borrada:
-- el NIT es único por empresa).
INSERT INTO tercero (empresa_id, tipo_documento, numero_documento, dv, razon_social,
                     tipo_persona, es_cliente, es_proveedor, activo, municipio,
                     created_at, updated_at)
SELECT e.id, 'NIT', '800197268', '4', 'DIRECCIÓN DE IMPUESTOS Y ADUANAS NACIONALES - DIAN',
       'JURIDICA', false, true, true, 'Bogotá, D.C.', now(), now()
  FROM empresa e
 WHERE NOT EXISTS (SELECT 1 FROM tercero t
                    WHERE t.empresa_id = e.id AND t.numero_documento = '800197268');

-- IVA e INC → DIAN (solo los que no tienen acreedor).
UPDATE impuesto i
   SET tercero_acreedor_id = t.id
  FROM tercero t
 WHERE t.empresa_id = i.empresa_id
   AND t.numero_documento = '800197268'
   AND t.deleted_at IS NULL
   AND i.tipo IN ('IVA', 'INC')
   AND i.tercero_acreedor_id IS NULL;

-- Retefuente y reteIVA → DIAN. El reteICA se paga al municipio: queda para
-- que el contador elija la secretaría de hacienda de cada tarifa.
UPDATE tarifa_retencion r
   SET tercero_acreedor_id = t.id
  FROM tercero t
 WHERE t.empresa_id = r.empresa_id
   AND t.numero_documento = '800197268'
   AND t.deleted_at IS NULL
   AND r.tipo IN ('RETEFUENTE', 'RETEIVA')
   AND r.tercero_acreedor_id IS NULL;

-- Pago de una declaración: IVA (300), retención (350) o reteICA de un municipio.
CREATE TABLE IF NOT EXISTS declaracion_pago (
    id                   BIGSERIAL      PRIMARY KEY,
    empresa_id           INT            NOT NULL REFERENCES empresa(id),
    consecutivo          INT            NOT NULL,
    -- IVA | RETENCION | RETEICA
    tipo                 VARCHAR(12)    NOT NULL,
    desde                DATE           NOT NULL,
    hasta                DATE           NOT NULL,
    tercero_acreedor_id  BIGINT         NOT NULL REFERENCES tercero(id),
    fecha                DATE           NOT NULL,
    numero_formulario    VARCHAR(30),
    valor                NUMERIC(18,2)  NOT NULL,
    -- Cuenta de donde sale la plata: la bancaria o una cuenta contable elegida.
    metodo_pago          VARCHAR(20)    NOT NULL,
    cuenta_bancaria_id   BIGINT,
    cuenta_contable_id   BIGINT,
    -- PAGADA | ANULADA
    estado               VARCHAR(10)    NOT NULL DEFAULT 'PAGADA',
    usuario_id           INT,
    created_at           TIMESTAMP      NOT NULL DEFAULT now(),
    anulado_por          INT,
    anulado_at           TIMESTAMP,
    motivo_anulacion     VARCHAR(300)
);

CREATE INDEX IF NOT EXISTS idx_declaracion_pago_periodo
    ON declaracion_pago (empresa_id, tipo, desde, hasta);

-- Un solo pago vigente por declaración y acreedor.
CREATE UNIQUE INDEX IF NOT EXISTS uq_declaracion_pago_vigente
    ON declaracion_pago (empresa_id, tipo, desde, hasta, tercero_acreedor_id)
    WHERE estado = 'PAGADA';

-- Lo que el pago cierra de cada cuenta: valor > 0 se debita (saldo crédito
-- del impuesto por pagar), valor < 0 se acredita (IVA descontable, retenciones
-- que le practicaron).
CREATE TABLE IF NOT EXISTS declaracion_pago_cuenta (
    id         BIGSERIAL      PRIMARY KEY,
    pago_id    BIGINT         NOT NULL REFERENCES declaracion_pago(id) ON DELETE CASCADE,
    cuenta_id  BIGINT         NOT NULL REFERENCES plan_cuenta(id),
    valor      NUMERIC(18,2)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_declaracion_pago_cuenta_pago
    ON declaracion_pago_cuenta (pago_id);
