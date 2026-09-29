-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Carritos Abandonados" (bajo Reportes)             ║
-- ║                                                                      ║
-- ║  Carritos del POS que se vaciaron sin vender: cuánto duraron y qué   ║
-- ║  productos tenían.                                                   ║
-- ║                                                                      ║
-- ║  El frontend oculta el ítem del sidebar hasta que su submódulo esté  ║
-- ║  activo para la empresa (shared/utils/modules-fiilter.ts compara     ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).           ║
-- ║  normalize("Carritos Abandonados") = "carritos-abandonados".         ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo el módulo Reportes (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id,
       'Carritos Abandonados',
       'carritos-abandonados',
       'Carritos del POS vaciados sin vender: cuánto duraron y qué productos tenían',
       true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       NOW(), NOW()
FROM modulos m
WHERE LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'reportes'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'carritos-abandonados'
  );

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo Reportes.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'carritos-abandonados'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'carritos-abandonados';
