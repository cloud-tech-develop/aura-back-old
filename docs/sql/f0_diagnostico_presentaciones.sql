SET default_transaction_read_only = on;
\echo '== 1. productos con presentaciones por empresa'
SELECT p.empresa_id, e.razon_social AS empresa, COUNT(DISTINCT p.id) AS productos, COUNT(pp.id) AS presentaciones
FROM producto_presentacion pp
JOIN producto p ON p.id = pp.producto_id
LEFT JOIN empresa e ON e.id = p.empresa_id
WHERE pp.activo = true AND p.deleted_at IS NULL
GROUP BY p.empresa_id, e.razon_social ORDER BY productos DESC;

\echo '== 2. clasificacion (precio primero, nombre despues)'
WITH c AS (
  SELECT p.empresa_id, p.id AS producto_id, p.nombre, um.nombre AS base, p.precio AS precio_base,
         pp.id AS pres_id, pp.nombre AS presentacion, pp.factor_conversion AS factor, pp.precio AS precio_pres,
         CASE
           WHEN pp.factor_conversion = 1 THEN 'FACTOR_1'
           WHEN COALESCE(pp.precio,0) > 0 AND COALESCE(p.precio,0) > 0 AND pp.factor_conversion > 1
                THEN CASE WHEN pp.precio > p.precio THEN 'GRANDE' ELSE 'PEQUENA' END
           WHEN pp.factor_conversion > 1 AND pp.nombre ~* '(caja|paca|bulto|display|six|docena|cubeta|blister|fardo|canasta|pack|x ?[0-9]{2,})'
                THEN 'GRANDE_NOMBRE'
           WHEN pp.factor_conversion > 1 AND pp.nombre ~* '(unidad|und|kilo|kg|gramo|gr\b|libra|lb\b|suelt|metro|mt\b)'
                THEN 'PEQUENA_NOMBRE'
           ELSE 'REVISAR'
         END AS lectura
  FROM producto_presentacion pp
  JOIN producto p ON p.id = pp.producto_id
  LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
  WHERE pp.activo = true AND p.deleted_at IS NULL
)
SELECT empresa_id, lectura, COUNT(*) AS presentaciones FROM c GROUP BY 1,2 ORDER BY 1,2;

\echo '== 2b. detalle (max 150)'
SELECT p.empresa_id AS emp, p.id, LEFT(p.nombre,35) AS nombre, um.nombre AS base, p.precio AS precio_base,
       pp.id AS pres_id, LEFT(pp.nombre,25) AS presentacion, pp.factor_conversion AS factor, pp.precio AS precio_pres,
       COALESCE((SELECT SUM(i.stock_actual) FROM inventario i WHERE i.producto_id = p.id),0) AS stock
FROM producto_presentacion pp
JOIN producto p ON p.id = pp.producto_id
LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
WHERE pp.activo = true AND p.deleted_at IS NULL
ORDER BY p.empresa_id, p.nombre LIMIT 150;

\echo '== 3. ventas con presentacion por empresa'
SELECT p.empresa_id, COUNT(*) AS lineas, SUM(vd.cantidad) AS cantidad,
       MIN(v.fecha_emision)::date AS desde, MAX(v.fecha_emision)::date AS hasta
FROM venta_detalle vd
JOIN venta v ON v.id = vd.venta_id
JOIN producto_presentacion pp ON pp.id = vd.producto_presentacion_id
JOIN producto p ON p.id = pp.producto_id
GROUP BY 1 ORDER BY 1;

\echo '== 3b. otras referencias a presentaciones'
SELECT 'producto_precio' AS tabla, COUNT(*) FROM producto_precio WHERE producto_presentacion_id IS NOT NULL
UNION ALL SELECT 'precio_volumen', COUNT(*) FROM precio_volumen WHERE producto_presentacion_id IS NOT NULL
UNION ALL SELECT 'precio_cliente', COUNT(*) FROM precio_cliente WHERE producto_presentacion_id IS NOT NULL
UNION ALL SELECT 'producto_composicion', COUNT(*) FROM producto_composicion WHERE producto_presentacion_id IS NOT NULL
UNION ALL SELECT 'devolucion_detalle', COUNT(*) FROM devolucion_detalle WHERE producto_presentacion_id IS NOT NULL;

\echo '== 3c. productos con presentacion y stock con decimales'
SELECT p.empresa_id, COUNT(DISTINCT p.id) AS productos
FROM producto p
JOIN inventario i ON i.producto_id = p.id
WHERE EXISTS (SELECT 1 FROM producto_presentacion pp WHERE pp.producto_id = p.id AND pp.activo)
  AND i.stock_actual <> TRUNC(i.stock_actual)
GROUP BY 1;

\echo '== 4. codigos de presentacion que chocan con un producto de la misma empresa'
SELECT p.empresa_id, pp.codigo_barras, pp.id AS pres_id, p2.id AS producto_mismo_codigo
FROM producto_presentacion pp
JOIN producto p  ON p.id = pp.producto_id
JOIN producto p2 ON p2.empresa_id = p.empresa_id AND p2.codigo_barras = pp.codigo_barras AND p2.deleted_at IS NULL
WHERE pp.codigo_barras IS NOT NULL AND pp.codigo_barras <> '';

\echo '== 4b. restricciones en producto_presentacion'
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'producto_presentacion'::regclass;
