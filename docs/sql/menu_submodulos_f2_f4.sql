-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: submódulos de las fases 2–4 (catálogo, activos, contador)     ║
-- ║                                                                      ║
-- ║  El sidebar oculta el ítem cuyo normalize(label) no esté entre los   ║
-- ║  submódulos activos de la empresa (shared/utils/modules-fiilter.ts). ║
-- ║                                                                      ║
-- ║    Contabilidad › Categorías Contables       categorias-contables     ║
-- ║    Contabilidad › Parametrización Contable   parametrizacion-contable ║
-- ║    Contabilidad › Balance de Prueba          balance-de-prueba        ║
-- ║    Contabilidad › Herramientas del Contador  herramientas-del-contador║
-- ║    Compras      › Sugerido de Compra         sugerido-de-compra       ║
-- ║                                                                      ║
-- ║  La ficha y el informe de activos cuelgan de Activos Fijos (sin ítem).║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo de submódulos.
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, v.nombre, v.codigo, v.descripcion, true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + v.orden,
       NOW(), NOW()
FROM (VALUES
        ('contabilidad', 'Categorías Contables',      'categorias-contables',      'Cuentas por clase de ítem: mercancía, activos, gastos, diferidos', 1),
        ('contabilidad', 'Parametrización Contable',  'parametrizacion-contable',  'Cuenta de cada concepto, forma de pago e impuesto',               2),
        ('contabilidad', 'Balance de Prueba',         'balance-de-prueba',         'Saldo anterior, movimientos y saldo final, con comparativo',        3),
        ('contabilidad', 'Herramientas del Contador', 'herramientas-del-contador', 'Traslado de cuentas y fusión de terceros',                          4),
        ('compras',      'Sugerido de Compra',        'sugerido-de-compra',        'Productos en su punto de reorden y cuánto pedir',                   1)
     ) AS v(modulo, nombre, codigo, descripcion, orden)
JOIN modulos m ON LOWER(TRANSLATE(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = v.modulo
WHERE NOT EXISTS (
    SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = v.codigo
);

-- 2) Activación por empresa: toda empresa que ya tenga activo el módulo.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE s.codigo IN ('categorias-contables', 'parametrizacion-contable', 'balance-de-prueba',
                   'herramientas-del-contador', 'sugerido-de-compra')
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT m.codigo AS modulo, s.codigo, COUNT(es.*) AS empresas
--   FROM submodulos s JOIN modulos m ON m.id = s.modulo_id
--   LEFT JOIN empresa_submodulo es ON es.submodulo_id = s.id AND es.activo
--  WHERE s.codigo IN ('categorias-contables', 'parametrizacion-contable', 'balance-de-prueba',
--                     'herramientas-del-contador', 'sugerido-de-compra')
--  GROUP BY 1, 2;
