-- ============================================================================
--  Limpieza compra #854 — v2 (CORREGIDA)
--
--  POR QUÉ FALLÓ LA v1
--  Filtraba por origen_tipo='COMPRA' AND origen_id=854. Esas columnas las
--  empezó a poblar el commit d4e1257, que NO está desplegado (su build falla
--  por memoria). Producción corre el jar anterior y creó estas filas con
--  origen_tipo = NULL y origen_id = NULL, así que el WHERE no encontró nada.
--  El "0,0,0" de verificación contaba filas inexistentes bajo ese predicado:
--  no confirmaba borrado. Aquí se va por ID explícito.
--
--  CUIDADO: los ids 1314 y 1315 NO pertenecen a la compra #854 (la secuencia
--  salta de 1313 a 1316). NUNCA uses BETWEEN 1313 AND 1327.
-- ============================================================================

-- ############################################################################
-- ## PASO 0 — DIAGNÓSTICO (solo lectura). Corre esto y LÉELO.
-- ############################################################################

-- 0.1 Las 13 filas de la compra #854, con el estado real de las columnas origen.
--     Si origen_tipo sale NULL, queda confirmado por qué falló la v1.
SELECT id, tipo, monto, fecha, turno_caja_id,
       origen_tipo, origen_id,
       left(concepto, 45) AS concepto
FROM movimiento_caja
WHERE id IN (1313,1316,1317,1318,1319,1320,1321,1322,1323,1324,1325,1326,1327)
ORDER BY id;

-- 0.2 CONTROL — qué son 1314 y 1315. Confirma que NO son de la compra #854.
SELECT id, tipo, monto, turno_caja_id, left(concepto, 60) AS concepto
FROM movimiento_caja
WHERE id IN (1314, 1315);

-- 0.3 Red de seguridad: ¿hay más filas de la #854 fuera de esa lista de ids?
--     Busca por concepto, que sí está poblado siempre.
SELECT id, tipo, monto, turno_caja_id, origen_tipo, left(concepto,45) AS concepto
FROM movimiento_caja
WHERE concepto LIKE '%compra #854%' OR concepto LIKE '%Compra #854%'
ORDER BY id;
-- Si aquí salen ids que no están en 0.1, agrégalos a la lista del PASO 1.

-- 0.4 Neto actual (debe dar -47999.96 = un egreso legítimo)
SELECT count(*) AS filas,
       sum(CASE WHEN tipo='EGRESO' THEN -monto ELSE monto END) AS neto
FROM movimiento_caja
WHERE id IN (1313,1316,1317,1318,1319,1320,1321,1322,1323,1324,1325,1326,1327);

-- 0.5 Asientos: ¿origen_id está poblado aquí, o también es NULL?
--     NO asumas que sí — verifica antes de borrar por ese campo.
SELECT id, tipo_origen, estado, numero_comprobante, fecha, origen_id,
       total_debito, periodo_contable_id
FROM asiento_contable
WHERE origen_id = 854 AND tipo_origen IN ('COMPRA','ANULACION_COMPRA')
   OR descripcion LIKE '%compra #854%'
   OR descripcion LIKE '%Compra #854%'
ORDER BY id;

-- 0.6 Comprobante de egreso (mismo riesgo: 'origen' puede estar NULL)
SELECT id, numero_comprobante, tipo, monto, anulado, origen, origen_id,
       left(concepto,45) AS concepto
FROM comprobante_caja
WHERE (origen = 'COMPRA' AND origen_id = 854)
   OR concepto LIKE '%ompra #854%'
ORDER BY id;

-- 0.7 Turno 715 está ABIERTA, así que borrar no rompe ningún arqueo firmado.
SELECT id, estado, fecha_apertura FROM turno_caja WHERE id = 715;


-- ############################################################################
-- ## PASO 1 — BORRADO (conserva el neto)
-- ##
-- ## Borra los 6 INGRESOS de reverso y 6 de los 7 EGRESOS.
-- ## CONSERVA el id 1327 = el último EGRESO = el pago real de la compra.
-- ## El saldo del turno NO cambia.
-- ############################################################################

BEGIN;

DELETE FROM movimiento_caja
WHERE id IN (
    1313,              -- EGRESO  16:17:33
    1316, 1317,        -- reverso + egreso  16:32:07
    1318, 1319,        -- reverso + egreso  16:35:14
    1320, 1321,        -- reverso + egreso  16:38:48
    1322, 1323,        -- reverso + egreso  16:38:54
    1324, 1325,        -- reverso + egreso  16:39:32
    1326               -- reverso           16:42:27
);                     -- 1327 (EGRESO 16:42:27) SE QUEDA — es el pago vigente
-- Esperado: DELETE 12

-- VERIFICACIÓN REAL (esta sí distingue borrado de no-match)
SELECT count(*) AS filas_restantes,
       sum(CASE WHEN tipo='EGRESO' THEN -monto ELSE monto END) AS neto
FROM movimiento_caja
WHERE id IN (1313,1316,1317,1318,1319,1320,1321,1322,1323,1324,1325,1326,1327);
-- Esperado: filas_restantes = 1 , neto = -47999.96

SELECT id, tipo, monto, concepto FROM movimiento_caja WHERE id = 1327;
-- Esperado: 1327 | EGRESO | 47999.96 | Compra #854 - Pago contado a proveedor

ROLLBACK;   -- <<<<<< CAMBIA ESTO A  COMMIT;  CUANDO LOS NÚMEROS CUADREN
            --        (la v1 se quedó en ROLLBACK — revisa que lo cambiaste)


-- ############################################################################
-- ## PASO 2 — comprueba en la app, NO en SQL
-- ##
-- ## Vuelve a pedir el resumen del turno 715. Debe quedar:
-- ##   totalIngresos  : 0
-- ##   totalEgresos   : 47999.96
-- ##   totalEsperado  : 1380049.37   <-- EL MISMO DE ANTES, al centavo
-- ##
-- ## Que totalEsperado no se mueva es la prueba de que no tocaste plata:
-- ##   600000.00 (base) + 828049.33 (efectivo) - 47999.96 (egreso) = 1380049.37
-- ############################################################################
