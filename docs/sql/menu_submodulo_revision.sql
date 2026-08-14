-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Revisión de Comprobantes" (bandeja E3)             ║
-- ║                                                                      ║
-- ║  El frontend oculta cualquier ítem del sidebar cuyo submódulo no     ║
-- ║  esté activo para la empresa (shared/utils/modules-fiilter.ts:       ║
-- ║  compara normalize(item.label) contra el submodulo_codigo activo).   ║
-- ║  normalize("Revisión de Comprobantes") = "revision-de-comprobantes". ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo el módulo Contabilidad (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id,
       'Revisión de Comprobantes',
       'revision-de-comprobantes',
       'Bandeja del contador: aprobar asientos automáticos en borrador',
       true,
       -- ubícalo justo después de "Asientos Contables"
       COALESCE((SELECT orden FROM submodulos WHERE modulo_id = m.id
                   AND codigo = 'asientos-contables'), 1) + 1,
       NOW(), NOW()
FROM modulos m
WHERE LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'contabilidad'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'revision-de-comprobantes'
  );

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo Contabilidad (mismo criterio que los demás ítems).
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos  m  ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'revision-de-comprobantes'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'revision-de-comprobantes';
