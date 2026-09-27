-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulo "Bodegas"                                          ║
-- ║                                                                      ║
-- ║  El sidebar oculta el ítem hasta que su submódulo esté activo para   ║
-- ║  la empresa (shared/utils/modules-fiilter.ts compara                 ║
-- ║  normalize(item.label) contra el submodulo_codigo activo).           ║
-- ║  normalize("Bodegas") = "bodegas".                                   ║
-- ║                                                                      ║
-- ║  OJO: el filtro usa un Set GLOBAL de labels normalizados. Un label   ║
-- ║  repetido en otro módulo comparte visibilidad con este y no hay      ║
-- ║  forma de separarlos por rol. "Bodegas" no debe repetirse.           ║
-- ║                                                                      ║
-- ║  Se cuelga del mismo módulo donde ya vive el inventario.             ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea el submódulo junto al inventario (si no existe).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT ref.modulo_id,
       'Bodegas',
       'bodegas',
       'Dónde vive el stock dentro de cada sucursal, con su responsable',
       true,
       ref.orden + 1,
       NOW(), NOW()
FROM (SELECT modulo_id, orden FROM submodulos
       WHERE codigo IN ('inventario', 'kardex', 'traslados')
       ORDER BY CASE codigo WHEN 'inventario' THEN 1 WHEN 'kardex' THEN 2 ELSE 3 END
       LIMIT 1) ref
WHERE NOT EXISTS (
    SELECT 1 FROM submodulos s
    WHERE s.modulo_id = ref.modulo_id AND s.codigo = 'bodegas'
);

-- 2) Activación por empresa: enciende el submódulo para toda empresa que ya
--    tenga activo el módulo al que quedó colgado.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m         ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo = 'bodegas'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT e.razon_social AS empresa, s.codigo, es.activo
-- FROM empresa_submodulo es
-- JOIN submodulos s ON s.id = es.submodulo_id
-- JOIN empresa e    ON e.id = es.empresa_id
-- WHERE s.codigo = 'bodegas';
