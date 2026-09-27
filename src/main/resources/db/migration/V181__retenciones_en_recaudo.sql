-- V181 — Retenciones que los clientes le practican a la empresa, al recaudar.
--
-- Un cliente agente retenedor paga la factura menos lo que retiene (renta, IVA,
-- ICA) y entrega el certificado. Hasta ahora ese valor no tenía dónde
-- registrarse: la cuenta por cobrar quedaba con un saldo que nunca se iba a
-- pagar y el anticipo de impuestos (1355) no crecía, así que la retención no
-- se descontaba en las declaraciones.
--
-- Cada retención se guarda como un abono vinculado al pago en efectivo o banco
-- (abono_origen_id), con medio de pago RETEFUENTE | RETEIVA | RETEICA. Así el
-- saldo de la cartera, que en todo el sistema se calcula como
-- total − SUM(abonos), la cuenta como pagada sin tocar esas consultas, y el
-- arqueo, que solo suma efectivo, no la ve.

ALTER TABLE abonos_cobrar ADD COLUMN IF NOT EXISTS abono_origen_id BIGINT;
ALTER TABLE abonos_cobrar ADD COLUMN IF NOT EXISTS base_retencion  NUMERIC(15,2);

CREATE INDEX IF NOT EXISTS idx_abonos_cobrar_origen
    ON abonos_cobrar (abono_origen_id)
    WHERE abono_origen_id IS NOT NULL;

-- Lo retenido por factura dentro de un recibo de caja (además del efectivo).
ALTER TABLE recibo_caja_aplicacion ADD COLUMN IF NOT EXISTS retenciones NUMERIC(15,2) NOT NULL DEFAULT 0;

-- Subcuentas del anticipo de impuestos, una por tipo de retención: la
-- declaración de renta, la de IVA y la de ICA descuentan cada una la suya.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id,
                         activa, auxiliar, es_medio_pago, created_at)
SELECT p.empresa_id, v.codigo, v.nombre, 'ACTIVO', 'DEBITO', 4, p.id, TRUE, TRUE, FALSE, now()
  FROM (VALUES ('135515', 'Retención en la fuente'),
               ('135517', 'Impuesto a las ventas retenido'),
               ('135518', 'Impuesto de industria y comercio retenido')) AS v(codigo, nombre)
  JOIN plan_cuenta p ON p.codigo = '1355'
 WHERE NOT EXISTS (SELECT 1 FROM plan_cuenta x
                    WHERE x.empresa_id = p.empresa_id AND x.codigo = v.codigo);

-- El concepto de retención que nos practican apuntaba a la 1355 misma.
UPDATE cuenta_config cc
   SET cuenta_id = s.id,
       updated_at = now()
  FROM plan_cuenta c, plan_cuenta s
 WHERE cc.concepto = 'RETEFUENTE_ASUMIDA'
   AND c.id = cc.cuenta_id
   AND c.codigo = '1355'
   AND s.empresa_id = cc.empresa_id
   AND s.codigo = '135515';

-- La 1355 queda agrupadora donde nada la usa para contabilizar.
UPDATE plan_cuenta c
   SET auxiliar = FALSE
 WHERE c.codigo = '1355'
   AND EXISTS (SELECT 1 FROM plan_cuenta s
                WHERE s.empresa_id = c.empresa_id AND s.codigo = '135515')
   AND NOT EXISTS (SELECT 1 FROM cuenta_config x WHERE x.cuenta_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM asiento_detalle d WHERE d.cuenta_id = c.id);
