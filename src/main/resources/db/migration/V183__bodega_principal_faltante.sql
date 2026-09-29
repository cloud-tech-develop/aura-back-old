-- V183 — Bodega principal para las sucursales que quedaron sin ella.
--
-- V172 le dio "Bodega Principal" solo a las sucursales que existían al correrla.
-- Hasta este cambio, crear una sucursal (o una empresa nueva) no creaba su
-- bodega, y esa sede no podía vender, comprar ni registrar mermas: "La sucursal
-- no tiene bodega principal". Desde ahora el backend la crea al crear la
-- sucursal; esto repara las que se crearon en el intermedio.
--
-- Idempotente: solo inserta donde la sucursal no tiene principal.

INSERT INTO bodega (empresa_id, sucursal_id, codigo, nombre, es_principal, permite_venta, activa)
SELECT s.empresa_id, s.id, 'BOD-' || s.id, 'Bodega Principal', TRUE, TRUE, TRUE
  FROM sucursal s
 WHERE NOT EXISTS (SELECT 1 FROM bodega b WHERE b.sucursal_id = s.id AND b.es_principal)
   -- Sin chocar con los únicos de nombre por sucursal y código por empresa.
   AND NOT EXISTS (SELECT 1 FROM bodega b
                    WHERE b.sucursal_id = s.id AND LOWER(b.nombre) = 'bodega principal')
   AND NOT EXISTS (SELECT 1 FROM bodega b
                    WHERE b.empresa_id = s.empresa_id AND UPPER(b.codigo) = 'BOD-' || s.id);

-- Si la sucursal ya tenía una bodega llamada "Bodega Principal" pero ninguna
-- marcada como principal, se marca esa.
UPDATE bodega b SET es_principal = TRUE
 WHERE LOWER(b.nombre) = 'bodega principal'
   AND NOT EXISTS (SELECT 1 FROM bodega p WHERE p.sucursal_id = b.sucursal_id AND p.es_principal);
