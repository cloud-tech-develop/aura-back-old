-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: Vinculaciones y Calendarios laborales (rediseño R2, V210)    ║
-- ║                                                                      ║
-- ║  Vinculaciones: el contrato completo del empleado (pantalla nueva).  ║
-- ║  Calendarios laborales: catálogo de días que se trabajan.            ║
-- ║                                                                      ║
-- ║  Claves: recursos-humanos.vinculaciones,                             ║
-- ║          recursos-humanos.calendarios-laborales                      ║
-- ║  Correr después de V210 y de menu_submodulos_nomina_r1_catalogos.sql ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea los submódulos bajo Recursos Humanos (si no existen).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, v.nombre, v.codigo, v.descripcion, true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + v.orden,
       NOW(), NOW()
FROM modulos m
JOIN (VALUES
    ('Vinculaciones',         'vinculaciones',         'Contrato completo del empleado: tipo, cargo, jornada, prueba, jefe y soportes', 1),
    ('Calendarios laborales', 'calendarios-laborales', 'Días que se trabajan (lunes a viernes, a sábado, turnos)', 2)
) AS v(nombre, codigo, descripcion, orden) ON TRUE
WHERE m.codigo = 'recursos-humanos'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = v.codigo
  );

-- 2) Activación por empresa: los enciende para toda empresa que tenga
--    activo el módulo Recursos Humanos.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE m.codigo = 'recursos-humanos'
  AND s.codigo IN ('vinculaciones', 'calendarios-laborales')
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT m.codigo || '.' || s.codigo AS clave, COUNT(es.*) AS empresas
-- FROM modulos m
-- JOIN submodulos s ON s.modulo_id = m.id
-- LEFT JOIN empresa_submodulo es ON es.submodulo_id = s.id AND es.activo
-- WHERE m.codigo = 'recursos-humanos'
-- GROUP BY 1 ORDER BY 1;
