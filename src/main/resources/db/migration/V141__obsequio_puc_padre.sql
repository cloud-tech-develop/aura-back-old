-- ── V141: reparar la jerarquía de las cuentas sembradas por la V140 ─────────
--
-- La V140 insertó las cinco cuentas del obsequio en un solo INSERT resolviendo
-- `padre_id` con una subconsulta. Ese fue el error: las filas de un mismo
-- INSERT no se ven entre sí, así que cuando la fila de '5235' buscó a su padre
-- '52', ese '52' todavía no estaba confirmado y la subconsulta devolvió NULL.
-- El `ORDER BY` no ayuda: no cambia el snapshot contra el que se evalúa la
-- subconsulta.
--
-- Resultado: las cuentas EXISTEN (el motor de asientos resuelve bien, porque
-- busca por código) pero cuatro de las cinco quedaron colgando de la nada, y
-- el plan de cuentas se ve roto en el árbol de la UI.
--
-- Esta migración las reengancha. Es un UPDATE por nivel y solo toca filas con
-- padre_id NULL, así que es inofensiva donde la V140 haya corrido bien.

-- Nivel 2 → clase 5
UPDATE plan_cuenta hijo
   SET padre_id = padre.id
  FROM plan_cuenta padre
 WHERE hijo.codigo     = '52'
   AND hijo.padre_id   IS NULL
   AND padre.empresa_id = hijo.empresa_id
   AND padre.codigo     = '5';

-- Nivel 3 → 52
UPDATE plan_cuenta hijo
   SET padre_id = padre.id
  FROM plan_cuenta padre
 WHERE hijo.codigo     IN ('5235', '5295')
   AND hijo.padre_id   IS NULL
   AND padre.empresa_id = hijo.empresa_id
   AND padre.codigo     = '52';

-- Nivel 4 → su grupo
UPDATE plan_cuenta hijo
   SET padre_id = padre.id
  FROM plan_cuenta padre
 WHERE hijo.codigo     = '523550'
   AND hijo.padre_id   IS NULL
   AND padre.empresa_id = hijo.empresa_id
   AND padre.codigo     = '5235';

UPDATE plan_cuenta hijo
   SET padre_id = padre.id
  FROM plan_cuenta padre
 WHERE hijo.codigo     = '529505'
   AND hijo.padre_id   IS NULL
   AND padre.empresa_id = hijo.empresa_id
   AND padre.codigo     = '5295';

-- Red de seguridad: si una empresa se quedó sin alguna de las cinco cuentas
-- (la V140 pudo insertar unas y otras no), se completan aquí. Va por niveles
-- separados, que es justamente lo que le faltó a la V140.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT e.id, '52', 'Gastos Operacionales de Ventas', 'GASTO', 'DEBITO', 2,
       (SELECT p.id FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = '5' LIMIT 1),
       TRUE, FALSE, CURRENT_TIMESTAMP
  FROM empresa e
 WHERE EXISTS     (SELECT 1 FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = '5')
   AND NOT EXISTS (SELECT 1 FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = '52');

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT e.id, v.codigo, v.nombre, 'GASTO', 'DEBITO', 3,
       (SELECT p.id FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = '52' LIMIT 1),
       TRUE, TRUE, CURRENT_TIMESTAMP
  FROM empresa e
  CROSS JOIN (VALUES ('5235', 'Servicios'), ('5295', 'Diversos')) AS v(codigo, nombre)
 WHERE EXISTS     (SELECT 1 FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = '52')
   AND NOT EXISTS (SELECT 1 FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = v.codigo);

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT e.id, v.codigo, v.nombre, 'GASTO', 'DEBITO', 4,
       (SELECT p.id FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = v.codigo_padre LIMIT 1),
       TRUE, TRUE, CURRENT_TIMESTAMP
  FROM empresa e
  CROSS JOIN (VALUES
        ('523550', 'Publicidad Propaganda y Promocion',   '5235'),
        ('529505', 'IVA Asumido en Retiro de Inventario', '5295')
  ) AS v(codigo, nombre, codigo_padre)
 WHERE EXISTS     (SELECT 1 FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = v.codigo_padre)
   AND NOT EXISTS (SELECT 1 FROM plan_cuenta p WHERE p.empresa_id = e.id AND p.codigo = v.codigo);
