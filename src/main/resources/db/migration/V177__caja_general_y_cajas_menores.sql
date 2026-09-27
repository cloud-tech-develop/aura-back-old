-- V177 — Caja general (110505) y cajas menores (110510) según el PUC.
--
-- El seedPUC creaba "110505 Caja Menor", pero en el PUC colombiano 110505 es
-- Caja general y 110510 es Cajas menores. Además el efectivo del punto de venta
-- se contabilizaba en la 1105 misma, que al tener una subcuenta quedaba
-- recibiendo movimientos a la vez que su hija: lo primero que reprocha un
-- contador.
--
-- Queda así:
--   1105   Caja            agrupadora (no recibe movimientos nuevos)
--   110505 Caja general    el efectivo de las cajas del punto (concepto CAJA)
--   110510 Cajas menores   el fondo fijo del administrador
--
-- El saldo histórico de la 1105 NO se mueve aquí: trasladarlo es un asiento, y
-- lo decide el contador (nota contable DB 110505 · CR 1105).
--
-- Todo es condicional para no pisar lo que un contador ya haya ajustado a mano.
-- Idempotente.

-- 1. La "Caja Menor" sembrada como 110505 pasa a su código correcto. Se conserva
--    la fila (y con ella sus movimientos): solo cambian código y nombre.
UPDATE plan_cuenta p
   SET codigo = '110510',
       nombre = 'Cajas menores'
 WHERE p.codigo = '110505'
   AND p.nombre ILIKE '%menor%'
   AND NOT EXISTS (SELECT 1 FROM plan_cuenta x
                    WHERE x.empresa_id = p.empresa_id AND x.codigo = '110510');

-- 2. Caja general y cajas menores bajo la 1105, donde falten.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id,
                         activa, auxiliar, es_medio_pago, created_at)
SELECT p.empresa_id, v.codigo, v.nombre, 'ACTIVO', 'DEBITO', 4, p.id, TRUE, TRUE, TRUE, now()
  FROM (VALUES ('110505', 'Caja general'),
               ('110510', 'Cajas menores')) AS v(codigo, nombre)
  JOIN plan_cuenta p ON p.codigo = '1105'
 WHERE NOT EXISTS (SELECT 1 FROM plan_cuenta x
                    WHERE x.empresa_id = p.empresa_id AND x.codigo = v.codigo);

-- 3. El concepto CAJA y la forma de pago que apuntaban a la 1105 pasan a la
--    caja general. Solo si la 110505 de esa empresa ES la caja general: si un
--    contador la dejó como caja menor (porque ya tenía su propia 110510), no se
--    le manda el efectivo del punto a su fondo fijo.
UPDATE cuenta_config cc
   SET cuenta_id = g.id,
       updated_at = now()
  FROM plan_cuenta c, plan_cuenta g
 WHERE cc.concepto = 'CAJA'
   AND c.id = cc.cuenta_id
   AND c.codigo = '1105'
   AND g.empresa_id = cc.empresa_id
   AND g.codigo = '110505'
   AND g.nombre ILIKE '%general%';

UPDATE forma_pago_contable f
   SET cuenta_contable_id = g.id
  FROM plan_cuenta c, plan_cuenta g
 WHERE c.id = f.cuenta_contable_id
   AND c.codigo = '1105'
   AND g.empresa_id = f.empresa_id
   AND g.codigo = '110505'
   AND g.nombre ILIKE '%general%';

-- 4. El código ahora trae 110505 como cuenta por defecto del concepto CAJA. Una
--    empresa cuya 110505 no es la caja general conserva la 1105 fijándola en
--    cuenta_config, para que el cambio de default no le desvíe el efectivo.
INSERT INTO cuenta_config (empresa_id, concepto, cuenta_id, created_at)
SELECT c.empresa_id, 'CAJA', c.id, now()
  FROM plan_cuenta c
 WHERE c.codigo = '1105'
   AND NOT EXISTS (SELECT 1 FROM cuenta_config x
                    WHERE x.empresa_id = c.empresa_id AND x.concepto = 'CAJA')
   AND NOT EXISTS (SELECT 1 FROM plan_cuenta g
                    WHERE g.empresa_id = c.empresa_id AND g.codigo = '110505'
                      AND g.nombre ILIKE '%general%');

-- 5. La 1105 se vuelve agrupadora donde ya nada la usa para contabilizar.
UPDATE plan_cuenta c
   SET auxiliar = FALSE,
       es_medio_pago = FALSE
 WHERE c.codigo = '1105'
   AND EXISTS (SELECT 1 FROM plan_cuenta g
                WHERE g.empresa_id = c.empresa_id AND g.codigo = '110505'
                  AND g.nombre ILIKE '%general%')
   AND NOT EXISTS (SELECT 1 FROM cuenta_config x WHERE x.cuenta_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM forma_pago_contable f WHERE f.cuenta_contable_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM cuenta_bancaria b WHERE b.cuenta_contable_id = c.id);
