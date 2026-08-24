-- ============================================================================
--  Compra #854 — borrar los 13 movimientos de caja. FINAL.
--
--  Confirmado en producción: origen_tipo y origen_id están en NULL en las 13
--  filas (el jar desplegado es anterior a d4e1257, que fue quien empezó a
--  poblarlas). Por eso se va por ID explícito y no por origen_tipo.
--
--  ALCANCE: exactamente estos 13 ids. Nada más.
--  Los ids 1314 y 1315 NO son de la compra #854 — por eso hay lista y no
--  un BETWEEN, que se los llevaría por delante.
--
--  EFECTO EN EL SALDO: se va también el egreso vigente (1327), así que la
--  caja del turno 715 sube 47.999,96.
--      antes:   600.000,00 + 828.049,33 - 47.999,96 = 1.380.049,37
--      después: 600.000,00 + 828.049,33             = 1.428.049,33
-- ============================================================================

BEGIN;

DELETE FROM movimiento_caja
WHERE id IN (1313, 1316, 1317, 1318, 1319, 1320, 1321, 1322,
             1323, 1324, 1325, 1326, 1327);
-- Esperado: DELETE 13
-- Si dice DELETE 0 -> no llegaste a COMMIT la vez pasada, o los ids cambiaron.
-- Si dice más de 13 -> PARA y haz ROLLBACK.

-- ── Verificación 1: no queda ninguna de las 13 ──────────────────────────────
SELECT count(*) AS deben_ser_cero
FROM movimiento_caja
WHERE id IN (1313,1316,1317,1318,1319,1320,1321,1322,1323,1324,1325,1326,1327);
-- Esperado: 0

-- ── Verificación 2: no quedó ningún rastro de la #854 por concepto ──────────
SELECT count(*) AS deben_ser_cero
FROM movimiento_caja
WHERE concepto LIKE '%ompra #854%';
-- Esperado: 0

-- ── Verificación 3: 1314 y 1315 siguen intactas ─────────────────────────────
SELECT id, tipo, monto, left(concepto,50) AS concepto
FROM movimiento_caja
WHERE id IN (1314, 1315);
-- Esperado: las mismas 2 filas que viste en el diagnóstico. Si faltan, ROLLBACK.

-- ── Verificación 4: el turno 715 ya no tiene ingresos ni egresos ────────────
SELECT tipo, count(*) AS filas, sum(monto) AS total
FROM movimiento_caja
WHERE turno_caja_id = 715
GROUP BY tipo;
-- Esperado: sin filas (o solo lo que no venga de la compra #854)

ROLLBACK;   -- <<<<<<<<<< CÁMBIALO A  COMMIT;  <<<<<<<<<<
            -- Sin esto no se borra nada. Fue lo que pasó la vez pasada.

-- ============================================================================
--  DESPUÉS DEL COMMIT — comprobar en la app, no en SQL:
--  pide el resumen del turno 715 y confirma
--      movimientos    : []
--      totalIngresos  : 0
--      totalEgresos   : 0
--      totalEsperado  : 1428049.33
-- ============================================================================
