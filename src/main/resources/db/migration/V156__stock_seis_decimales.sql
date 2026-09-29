-- ── V156: el stock y el kardex guardan 6 decimales ─────────────────────────
--
-- Vender una presentación divide la cantidad por su factor: 1 leche de una
-- paca x6 son 1/6 = 0.166667 pacas. Con 2 decimales se guardaba 0.17, así que
-- cada leche suelta descontaba de más (6 leches = 1.02 pacas) y cada venta de
-- varias descontaba de menos (5 leches = 0.83). El inventario se desviaba del
-- conteo físico con cualquier factor que no divida a 100 (3, 6, 8, 12, 24…).
--
-- 6 decimales es la misma escala con la que VentaServiceImpl hace la división
-- y con la que producto_composicion ya guarda el consumo de cada componente.
--
-- Ampliar la escala no pierde datos ni cambia los valores ya guardados. Postgres
-- no reescribe la tabla si la columna ya tiene este tipo, así que re-correrla
-- es inofensivo.

ALTER TABLE inventario            ALTER COLUMN stock_actual   TYPE NUMERIC(38,6);
ALTER TABLE lote                  ALTER COLUMN stock_actual   TYPE NUMERIC(38,6);
ALTER TABLE movimiento_inventario ALTER COLUMN cantidad       TYPE NUMERIC(38,6);
ALTER TABLE movimiento_inventario ALTER COLUMN saldo_anterior TYPE NUMERIC(38,6);
ALTER TABLE movimiento_inventario ALTER COLUMN saldo_nuevo    TYPE NUMERIC(38,6);
