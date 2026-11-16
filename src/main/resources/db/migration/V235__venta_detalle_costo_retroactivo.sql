-- V235 — Costo de las ventas viejas que no lo tienen.
--
-- venta_detalle.costo_linea congela el costo al vender, pero hay líneas viejas en
-- null. Los reportes de utilidad las calculaban con el costo de HOY del producto,
-- así que la utilidad de un mes pasado cambiaba cada vez que llegaba una compra.
--
-- A cada línea en null se le pone el costo que el producto tenía en la fecha de la
-- venta, buscando en este orden:
--   1. producto_costo_historial: el último costo registrado hasta esa fecha (V226).
--   2. compra_detalle: el costo neto (sin IVA, con descuento) de la última compra
--      recibida hasta esa fecha.
--   3. producto_costo_historial: el costo que tenía antes del primer cambio
--      registrado (para ventas anteriores al historial sin compras previas).
--   4. producto.costo actual, si es mayor que cero.
-- Si nada de eso existe, la línea sigue en null (el reporte la marca como "sin costo").
--
-- Un producto con receta suma el costo de sus componentes. La cantidad se pasa a
-- unidad base con el factor de la presentación vendida, como hace la venta.
--
-- Las devoluciones de esas líneas también reciben su costo (devolucion_detalle.
-- costo_linea, V234), para que al anular una devolución el costo vuelva a la venta.
--
-- Idempotente: solo toca filas con costo_linea en null. Las funciones son de apoyo
-- y se borran al final.

CREATE OR REPLACE FUNCTION aura_v235_costo_unitario(p_producto BIGINT, p_fecha TIMESTAMP)
RETURNS NUMERIC LANGUAGE sql STABLE AS $$
    SELECT COALESCE(
        (SELECT h.costo_nuevo
           FROM producto_costo_historial h
          WHERE h.producto_id = p_producto AND h.fecha <= p_fecha
          ORDER BY h.fecha DESC, h.id DESC LIMIT 1),
        (SELECT cd.subtotal_linea / cd.cantidad
           FROM compra_detalle cd
           JOIN compra c ON c.id = cd.compra_id
          WHERE cd.producto_id = p_producto
            AND c.estado = 'RECIBIDA'
            AND c.fecha <= p_fecha
            AND cd.cantidad > 0 AND cd.subtotal_linea > 0
          ORDER BY c.fecha DESC, cd.id DESC LIMIT 1),
        (SELECT h.costo_anterior
           FROM producto_costo_historial h
          WHERE h.producto_id = p_producto AND h.costo_anterior > 0
          ORDER BY h.fecha, h.id LIMIT 1),
        (SELECT NULLIF(p.costo, 0) FROM producto p WHERE p.id = p_producto)
    )
$$;

CREATE OR REPLACE FUNCTION aura_v235_costo_linea(p_producto BIGINT, p_cantidad_base NUMERIC, p_fecha TIMESTAMP)
RETURNS NUMERIC LANGUAGE sql STABLE AS $$
    SELECT CASE
        WHEN EXISTS (SELECT 1 FROM producto_composicion pc WHERE pc.producto_padre_id = p_producto)
            THEN (SELECT SUM(p_cantidad_base * pc.cantidad * aura_v235_costo_unitario(pc.producto_hijo_id::BIGINT, p_fecha))
                    FROM producto_composicion pc
                   WHERE pc.producto_padre_id = p_producto)
        ELSE p_cantidad_base * aura_v235_costo_unitario(p_producto, p_fecha)
    END
$$;

-- Devoluciones vigentes de líneas sin costo: su parte, antes de llenar la línea.
-- La línea de la venta es la primera de ese producto, igual que en DevolucionServiceImpl.
UPDATE devolucion_detalle dd
   SET costo_linea = ROUND(aura_v235_costo_linea(
           dd.producto_id::BIGINT,
           (dd.cantidad * COALESCE((SELECT NULLIF(pp.factor_conversion, 0)
                                      FROM producto_presentacion pp
                                     WHERE pp.id = dd.producto_presentacion_id), 1))::NUMERIC,
           v.fecha_emision::TIMESTAMP), 2)
  FROM devolucion d
  JOIN venta v ON v.id = d.venta_id
  JOIN venta_detalle vd ON vd.venta_id = d.venta_id
 WHERE dd.devolucion_id = d.id
   AND vd.id = (SELECT MIN(x.id) FROM venta_detalle x
                 WHERE x.venta_id = d.venta_id AND x.producto_id = dd.producto_id)
   AND COALESCE(d.estado, '') <> 'ANULADA'
   AND dd.costo_linea IS NULL
   AND vd.costo_linea IS NULL;

-- Las líneas de venta sin costo (su cantidad ya viene rebajada por las devoluciones).
UPDATE venta_detalle vd
   SET costo_linea = ROUND(aura_v235_costo_linea(
           vd.producto_id::BIGINT,
           (vd.cantidad * COALESCE((SELECT NULLIF(pp.factor_conversion, 0)
                                      FROM producto_presentacion pp
                                     WHERE pp.id = vd.producto_presentacion_id), 1))::NUMERIC,
           v.fecha_emision::TIMESTAMP), 2)
  FROM venta v
 WHERE v.id = vd.venta_id
   AND vd.costo_linea IS NULL
   AND vd.producto_id IS NOT NULL;

DROP FUNCTION IF EXISTS aura_v235_costo_linea(BIGINT, NUMERIC, TIMESTAMP);
DROP FUNCTION IF EXISTS aura_v235_costo_unitario(BIGINT, TIMESTAMP);
