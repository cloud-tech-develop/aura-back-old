-- La pantalla /contabilidad/cierre dejó de llamarse "Cierre Contable": es un
-- informe de resultados entre dos fechas, no el cierre formal (ese vive en
-- Períodos contables).
--
-- El sidebar decide qué opción mostrar comparando normalize(label del menú)
-- contra submodulos.codigo, así que al renombrar el label hay que crear el
-- submódulo con el código nuevo ('resultados-del-periodo') y darle a cada
-- empresa el mismo permiso que tenía sobre el viejo ('cierre-contable'), o la
-- opción desaparece del menú. Idempotente: se puede correr varias veces.

-- 1) El submódulo nuevo, en el mismo módulo y posición del viejo.
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, orden, activo, created_at)
SELECT s.modulo_id, 'Resultados del Período', 'resultados-del-periodo',
       'Ventas, costos y utilidad entre dos fechas', s.orden, TRUE, NOW()
FROM submodulos s
WHERE s.codigo = 'cierre-contable'
  AND NOT EXISTS (SELECT 1 FROM submodulos x WHERE x.codigo = 'resultados-del-periodo');

-- 2) Cada empresa conserva el permiso que ya tenía.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at)
SELECT es.empresa_id, nuevo.id, es.activo, NOW()
FROM empresa_submodulo es
JOIN submodulos viejo ON viejo.id = es.submodulo_id AND viejo.codigo = 'cierre-contable'
CROSS JOIN (SELECT id FROM submodulos WHERE codigo = 'resultados-del-periodo') nuevo
WHERE NOT EXISTS (
    SELECT 1 FROM empresa_submodulo x
    WHERE x.empresa_id = es.empresa_id AND x.submodulo_id = nuevo.id);

-- 3) El viejo se apaga: ya no hay ningún label del menú que le corresponda.
UPDATE submodulos SET activo = FALSE, updated_at = NOW()
WHERE codigo = 'cierre-contable' AND activo;
