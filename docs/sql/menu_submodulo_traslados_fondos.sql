-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Traslados de Fondos" (bajo Tesorería)             ║
-- ║                                                                      ║
-- ║  Mover plata entre cuentas propias: constituir y reembolsar la caja  ║
-- ║  menor, consignar el efectivo del día al banco, pasar dinero entre   ║
-- ║  bancos. No confundir con "Traslados" de inventario, que vive en su  ║
-- ║  propio módulo y mueve productos entre sucursales.                   ║
-- ║                                                                      ║
-- ║  El frontend oculta el ítem del sidebar hasta que su submódulo esté  ║
-- ║  activo para la empresa (shared/utils/modules-fiilter.ts compara     ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).           ║
-- ║  normalize("Traslados de Fondos") = "traslados-de-fondos".           ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo el módulo Tesorería (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id,
       'Traslados de Fondos',
       'traslados-de-fondos',
       'Constituye y reembolsa la caja menor, consigna el efectivo del día y mueve dinero entre cuentas propias',
       true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       NOW(), NOW()
FROM modulos m
WHERE LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'tesoreria'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'traslados-de-fondos'
  );

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo Tesorería.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'traslados-de-fondos'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'traslados-de-fondos';
