-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Gastos Reporte" (bajo Reportes)                    ║
-- ║                                                                      ║
-- ║  Gastos agrupados por categoría, tercero, centro de costo, cuenta,   ║
-- ║  mes o sucursal, más el detalle uno a uno con su soporte, IVA y      ║
-- ║  retenciones. Deducible y no deducible salen siempre separados: es   ║
-- ║  la partición que el contador necesita para la declaración de renta. ║
-- ║                                                                      ║
-- ║  El frontend oculta el ítem del sidebar hasta que su submódulo esté  ║
-- ║  activo para la empresa (shared/utils/modules-fiilter.ts compara     ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).           ║
-- ║  normalize() quita acentos y cambia espacios por guiones, así que    ║
-- ║  para el label "Gastos Reporte" del sidebar el código tiene que ser  ║
-- ║  exactamente "gastos-reporte".                                       ║
-- ║                                                                      ║
-- ║  OJO: el label NO puede ser "Gastos" a secas. Ya existe un item con ese║
-- ║  nombre bajo otro módulo (ruta /gastos), y submodulosActivos es un Set║
-- ║  GLOBAL: dos items con el mismo label comparten visibilidad, así que uno║
-- ║  aparece o desaparece con el submodulo del otro.                     ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo el módulo Reportes (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id,
       'Gastos Reporte',
       'gastos-reporte',
       'En qué se fue la plata, con lo deducible separado de lo que no, y su exportación a Excel',
       true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       NOW(), NOW()
FROM modulos m
WHERE LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'reportes'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'gastos-reporte'
  );

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo Reportes.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'gastos-reporte'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'gastos-reporte';
