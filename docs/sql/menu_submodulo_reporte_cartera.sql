-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Estado de cuenta" (bajo Reportes)                  ║
-- ║                                                                      ║
-- ║  Quién debe qué y desde cuándo, en las dos caras: cuentas por        ║
-- ║  cobrar (clientes) y por pagar (proveedores). El saldo va repartido  ║
-- ║  por edades y cada documento abre sus abonos, con el dato de en qué  ║
-- ║  caja entró la plata.                                                ║
-- ║                                                                      ║
-- ║  El frontend oculta el ítem del sidebar hasta que su submódulo esté  ║
-- ║  activo para la empresa (shared/utils/modules-fiilter.ts compara     ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).           ║
-- ║  normalize() quita acentos y cambia espacios por guiones, así que    ║
-- ║  para el label "Estado de cuenta" del sidebar el código tiene que ser║
-- ║  exactamente "estado-de-cuenta".                                     ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo el módulo Reportes (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id,
       'Estado de cuenta',
       'estado-de-cuenta',
       'Cartera de clientes y proveedores por edades, con el detalle de cada abono y su exportación a Excel',
       true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       NOW(), NOW()
FROM modulos m
WHERE LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'reportes'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'estado-de-cuenta'
  );

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo Reportes.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'estado-de-cuenta'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'estado-de-cuenta';
