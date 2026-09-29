-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Reporte gerencial" (bajo Reportes)                 ║
-- ║                                                                      ║
-- ║  El informe de auditoría: cruza dos fuentes independientes de cada    ║
-- ║  cifra (arqueo vs. movimientos, cartera vs. abonos, kardex vs. stock, ║
-- ║  débitos vs. créditos) y reporta dónde no coinciden, con la plata en  ║
-- ║  riesgo, las causas más probables y el detalle donde mirarlo.         ║
-- ║  Se ve en pantalla y se descarga como PDF con gráficas.               ║
-- ║                                                                      ║
-- ║  El frontend oculta el ítem del sidebar hasta que su submódulo esté   ║
-- ║  activo para la empresa (shared/utils/modules-fiilter.ts compara      ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).            ║
-- ║  normalize() pasa a minúsculas, quita acentos y cambia espacios por   ║
-- ║  guiones, así que para el label "Reporte gerencial" del sidebar el    ║
-- ║  código tiene que ser exactamente "reporte-gerencial".                ║
-- ║                                                                      ║
-- ║  OJO: no confundir con el item "Reportes Avanzados" que ya existe en  ║
-- ║  el mismo grupo ("reportes-avanzados"). submodulosActivos es un Set   ║
-- ║  GLOBAL por label, así que dos items con el mismo nombre compartirían ║
-- ║  visibilidad.                                                         ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo el módulo Reportes (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id,
       'Reporte gerencial',
       'reporte-gerencial',
       'Auditoría del período: qué no cuadra en caja, cartera, inventario y contabilidad, con el PDF gerencial',
       true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       NOW(), NOW()
FROM modulos m
WHERE LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'reportes'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'reporte-gerencial'
  );

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo Reportes.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'reporte-gerencial'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'reporte-gerencial';
