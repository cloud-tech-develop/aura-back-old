-- ════════════════════════════════════════════════════════════════════════
-- F0 · Diagnóstico de lotes y seriales (PLAN_LOTES_SERIALES_CONSUMO_INTERNO)
-- SOLO LECTURA. Se puede correr en local (aura-pos) y en prod (aura-db).
-- La reparación del punto 6 está comentada: revisar el resultado antes.
-- ════════════════════════════════════════════════════════════════════════

-- 1. Resumen por empresa: cuántos productos usan lotes o seriales.
SELECT p.empresa_id,
       COUNT(*) FILTER (WHERE p.maneja_lotes)  AS productos_con_lote,
       COUNT(*) FILTER (WHERE p.maneja_serial) AS productos_con_serial,
       (SELECT COUNT(*) FROM lote l JOIN sucursal s ON s.id = l.sucursal_id
         WHERE s.empresa_id = p.empresa_id)    AS lotes,
       (SELECT COUNT(*) FROM serial_producto sp JOIN sucursal s ON s.id = sp.sucursal_id
         WHERE s.empresa_id = p.empresa_id)    AS seriales
FROM producto p
WHERE p.deleted_at IS NULL
GROUP BY p.empresa_id
HAVING COUNT(*) FILTER (WHERE p.maneja_lotes OR p.maneja_serial) > 0
    OR (SELECT COUNT(*) FROM lote l JOIN sucursal s ON s.id = l.sucursal_id
         WHERE s.empresa_id = p.empresa_id) > 0
ORDER BY p.empresa_id;

-- 2. Lotes contra inventario: diferencia por producto y sucursal.
--    diferencia > 0 → stock sin lote (F1 lo pondría en "SIN-LOTE").
--    diferencia < 0 → los lotes dicen más de lo que hay (solo se reporta).
SELECT s.empresa_id, s.nombre AS sucursal, p.id AS producto_id, p.nombre AS producto,
       p.maneja_lotes,
       COALESCE(i.stock_actual, 0)                                AS stock_inventario,
       COALESCE(SUM(l.stock_actual) FILTER (WHERE l.activo), 0)   AS stock_en_lotes,
       COALESCE(i.stock_actual, 0)
         - COALESCE(SUM(l.stock_actual) FILTER (WHERE l.activo), 0) AS diferencia,
       COUNT(l.id) FILTER (WHERE l.activo)                        AS lotes_activos
FROM producto p
JOIN sucursal s        ON s.empresa_id = p.empresa_id
LEFT JOIN inventario i ON i.producto_id = p.id AND i.sucursal_id = s.id
LEFT JOIN lote l       ON l.producto_id = p.id AND l.sucursal_id = s.id
WHERE p.deleted_at IS NULL
  AND (p.maneja_lotes OR l.id IS NOT NULL)
GROUP BY s.empresa_id, s.nombre, p.id, p.nombre, p.maneja_lotes, i.stock_actual
HAVING COALESCE(i.stock_actual, 0) <> 0 OR COUNT(l.id) > 0
ORDER BY s.empresa_id, ABS(COALESCE(i.stock_actual, 0)
         - COALESCE(SUM(l.stock_actual) FILTER (WHERE l.activo), 0)) DESC;

-- 3. Lotes raros: stock negativo, vencidos con stock, de otra empresa que el
--    producto, o de productos que ya no manejan lotes.
SELECT s.empresa_id, l.id AS lote_id, l.codigo_lote, p.nombre AS producto,
       l.fecha_vencimiento, l.stock_actual, l.activo,
       CASE
         WHEN l.stock_actual < 0                                   THEN 'STOCK NEGATIVO'
         WHEN p.empresa_id <> s.empresa_id                         THEN 'PRODUCTO DE OTRA EMPRESA'
         WHEN l.fecha_vencimiento < CURRENT_DATE AND l.stock_actual > 0 THEN 'VENCIDO CON STOCK'
         WHEN NOT COALESCE(p.maneja_lotes, false) AND l.stock_actual > 0 THEN 'PRODUCTO SIN MANEJO DE LOTES'
       END AS problema
FROM lote l
JOIN producto p ON p.id = l.producto_id
JOIN sucursal s ON s.id = l.sucursal_id
WHERE l.stock_actual < 0
   OR p.empresa_id <> s.empresa_id
   OR (l.fecha_vencimiento < CURRENT_DATE AND l.stock_actual > 0)
   OR (NOT COALESCE(p.maneja_lotes, false) AND l.stock_actual > 0)
ORDER BY s.empresa_id, problema, l.fecha_vencimiento;

-- 4. Documentos que descontaron el lote de OTRA empresa (el hueco de findById).
SELECT 'venta' AS documento, vd.venta_id AS documento_id, v.empresa_id AS empresa_documento,
       s.empresa_id AS empresa_lote, l.codigo_lote
FROM venta_detalle vd
JOIN venta v    ON v.id = vd.venta_id
JOIN lote l     ON l.id = vd.lote_id
JOIN sucursal s ON s.id = l.sucursal_id
WHERE v.empresa_id <> s.empresa_id
UNION ALL
SELECT 'merma', md.merma_id, m.empresa_id, s.empresa_id, l.codigo_lote
FROM merma_detalle md
JOIN merma m    ON m.id = md.merma_id
JOIN lote l     ON l.id = md.lote_id
JOIN sucursal s ON s.id = l.sucursal_id
WHERE m.empresa_id <> s.empresa_id
UNION ALL
SELECT 'obsequio', od.obsequio_id, o.empresa_id, s.empresa_id, l.codigo_lote
FROM obsequio_detalle od
JOIN obsequio o ON o.id = od.obsequio_id
JOIN lote l     ON l.id = od.lote_id
JOIN sucursal s ON s.id = l.sucursal_id
WHERE o.empresa_id <> s.empresa_id;

-- 5. Seriales contra stock: DISPONIBLES por producto y sucursal.
SELECT s.empresa_id, s.nombre AS sucursal, p.id AS producto_id, p.nombre AS producto,
       p.maneja_serial,
       COALESCE(i.stock_actual, 0)                                  AS stock_inventario,
       COUNT(sp.id) FILTER (WHERE sp.estado = 'DISPONIBLE')         AS seriales_disponibles,
       COUNT(sp.id) FILTER (WHERE sp.estado = 'VENDIDO')            AS seriales_vendidos,
       COALESCE(i.stock_actual, 0)
         - COUNT(sp.id) FILTER (WHERE sp.estado = 'DISPONIBLE')     AS diferencia
FROM producto p
JOIN sucursal s             ON s.empresa_id = p.empresa_id
LEFT JOIN inventario i      ON i.producto_id = p.id AND i.sucursal_id = s.id
LEFT JOIN serial_producto sp ON sp.producto_id = p.id AND sp.sucursal_id = s.id
WHERE p.deleted_at IS NULL
  AND (p.maneja_serial OR sp.id IS NOT NULL)
GROUP BY s.empresa_id, s.nombre, p.id, p.nombre, p.maneja_serial, i.stock_actual
HAVING COALESCE(i.stock_actual, 0) <> 0 OR COUNT(sp.id) > 0
ORDER BY s.empresa_id, p.nombre;

-- 6. Seriales liberados por el bug de anular venta: están DISPONIBLES pero
--    salieron en una venta que NO está anulada. Tienen que volver a VENDIDO.
SELECT s.empresa_id, sp.id AS serial_id, sp.serial, p.nombre AS producto,
       v.id AS venta_id, v.estado_venta, v.fecha_emision
FROM serial_producto sp
JOIN venta_detalle_serial vds ON vds.serial_producto_id = sp.id
JOIN venta_detalle vd         ON vd.id = vds.venta_detalle_id
JOIN venta v                  ON v.id = vd.venta_id
JOIN producto p               ON p.id = sp.producto_id
JOIN sucursal s               ON s.id = sp.sucursal_id
WHERE sp.estado = 'DISPONIBLE'
  AND v.estado_venta <> 'ANULADA'
ORDER BY s.empresa_id, v.id;

-- Reparación del punto 6 (revisar el SELECT antes; con backup en prod):
-- UPDATE serial_producto sp
--    SET estado = 'VENDIDO'
--   FROM venta_detalle_serial vds
--   JOIN venta_detalle vd ON vd.id = vds.venta_detalle_id
--   JOIN venta v          ON v.id = vd.venta_id
--  WHERE vds.serial_producto_id = sp.id
--    AND sp.estado = 'DISPONIBLE'
--    AND v.estado_venta <> 'ANULADA';

-- 7. Seriales repetidos dentro del mismo producto (ignorando mayúsculas y espacios).
SELECT p.empresa_id, p.nombre AS producto, UPPER(TRIM(sp.serial)) AS serial,
       COUNT(*) AS veces, STRING_AGG(sp.id::text || ':' || sp.estado, ', ') AS ids
FROM serial_producto sp
JOIN producto p ON p.id = sp.producto_id
GROUP BY p.empresa_id, p.nombre, sp.producto_id, UPPER(TRIM(sp.serial))
HAVING COUNT(*) > 1
ORDER BY p.empresa_id, veces DESC;
