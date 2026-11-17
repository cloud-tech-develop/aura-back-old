-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: Grupos de conceptos (PLAN_CONCEPTOS_CARGOS_NOMINA, F1/F4)    ║
-- ║                                                                      ║
-- ║  Catálogo simple de nómina (nomina_catalogo tipo GRUPO_CONCEPTO)     ║
-- ║  como submódulo aparte de Recursos Humanos, con su propio permiso:   ║
-- ║  recursos-humanos.grupos-concepto (igual que el `codigo` del ítem en ║
-- ║  sidebar.config.ts y PermisoRutas).                                  ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo Recursos Humanos (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Grupos de conceptos', 'grupos-concepto',
       'Agrupan conceptos para filtrarlos y agregarlos juntos a un cargo', true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       NOW(), NOW()
FROM modulos m
WHERE m.codigo = 'recursos-humanos'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'grupos-concepto'
  );

-- 2) Activación por empresa: lo enciende para toda empresa que tenga activo
--    el módulo Recursos Humanos.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE m.codigo = 'recursos-humanos'
  AND s.codigo = 'grupos-concepto'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT m.codigo || '.' || s.codigo AS clave, COUNT(es.*) AS empresas
--   FROM submodulos s JOIN modulos m ON m.id = s.modulo_id
--   LEFT JOIN empresa_submodulo es ON es.submodulo_id = s.id
--  WHERE s.codigo = 'grupos-concepto' GROUP BY 1;
