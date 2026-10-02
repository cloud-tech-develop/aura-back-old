-- ── V184: el costo del producto y el del kardex guardan 6 decimales ─────────
--
-- producto.costo deja de ser "el último costo de compra" y pasa a ser el costo
-- promedio ponderado (CostoPromedioService): cada compra mezcla lo que había
-- con lo que entra. Con 2 decimales cada mezcla redondea, y el error se
-- acumula compra tras compra hasta que el inventario valorizado deja de cuadrar
-- con la cuenta 1435. Con presentaciones pasa rápido: una paca de 6 a $10.000
-- son $1.666,666… por unidad.
--
-- movimiento_inventario.costo_historico sube igual para que el kardex guarde el
-- mismo costo unitario con que se valorizó la salida.
--
-- Ampliar la escala no pierde datos ni cambia los valores guardados; re-correrla
-- es inofensivo. Espejo en Laravel: 2026_07_03_000184_costo_promedio_seis_decimales.php

ALTER TABLE producto              ALTER COLUMN costo           TYPE NUMERIC(38,6);
ALTER TABLE movimiento_inventario ALTER COLUMN costo_historico TYPE NUMERIC(38,6);
