-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Obsequios"                                        ║
-- ║                                                                      ║
-- ║  El frontend oculta el ítem del sidebar hasta que su submódulo esté  ║
-- ║  activo para la empresa (shared/utils/modules-fiilter.ts compara     ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).           ║
-- ║  normalize("Obsequios") = "obsequios".                               ║
-- ║                                                                      ║
-- ║  Se cuelga del MISMO módulo donde ya vive "Mermas": el obsequio es   ║
-- ║  una salida de inventario, aunque su asiento sea de gasto de ventas. ║
-- ║  Si en esta BD el submódulo de mermas tiene otro código, ajústalo    ║
-- ║  abajo antes de correrlo.                                            ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo junto a Mermas (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT ref.modulo_id,
       'Obsequios',
       'obsequios',
       'Entrega de producto sin cobro (muestras, promociones, cortesías)',
       true,
       ref.orden + 1,
       NOW(), NOW()
FROM (SELECT modulo_id, orden FROM submodulos WHERE codigo = 'mermas' LIMIT 1) ref
WHERE NOT EXISTS (
    SELECT 1 FROM submodulos s
    WHERE s.modulo_id = ref.modulo_id AND s.codigo = 'obsequios'
);

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo al que quedó colgado.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m         ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'obsequios'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.nombre AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e   ON e.id = es.empresa_id
-- WHERE s.codigo = 'obsequios';
