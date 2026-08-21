-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Supervision de Caja" (bajo Caja)                  ║
-- ║                                                                      ║
-- ║  Qué entró a las cajas sin ser del turno: facturas viejas            ║
-- ║  autorizadas a mano, pagos cuyo documento es de otro día, cajas que  ║
-- ║  el sistema dedujo, y correcciones sobre arqueos cerrados.           ║
-- ║                                                                      ║
-- ║  No es una bandeja de pendientes: el origen ya es obligatorio al     ║
-- ║  registrar y el freno bloquea lo que no debe pasar. Es el sitio      ║
-- ║  donde el administrador revisa el rastro de lo que sí pasó.          ║
-- ║                                                                      ║
-- ║  El frontend oculta el ítem del sidebar hasta que su submódulo esté  ║
-- ║  activo para la empresa (shared/utils/modules-fiilter.ts compara     ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).           ║
-- ║  OJO: el label del sidebar es "Supervision de Caja" SIN tilde, y     ║
-- ║  normalize() solo quita acentos y cambia espacios por guiones, así   ║
-- ║  que el código tiene que ser "supervision-de-caja".                  ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo bajo el módulo Caja (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id,
       'Supervision de Caja',
       'supervision-de-caja',
       'Revisa qué entró a las cajas sin ser del turno: documentos autorizados a mano, pagos de otra fecha y correcciones de arqueos cerrados',
       true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       NOW(), NOW()
FROM modulos m
WHERE LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = 'caja'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = 'supervision-de-caja'
  );

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo Caja.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'supervision-de-caja'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'supervision-de-caja';
