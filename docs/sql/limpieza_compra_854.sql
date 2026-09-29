-- ============================================================================
--  Limpieza del ruido de ediciones — compra #854
--  Generado 2026-08-22. Ejecutar en PRODUCCIÓN a mano, paso por paso.
--
--  QUÉ PASÓ
--  Cada edición de una compra de contado reversa el pago anterior y registra
--  el nuevo (CompraServiceImpl.revertirPagosAnteriores + registrarEgresosDeCaja).
--  Siete ediciones dejaron 7 EGRESOS y 6 INGRESOS de $48.000.
--
--  OJO: ESO NETEA AL VALOR CORRECTO (-48.000). El saldo de caja está BIEN.
--  Lo que sobra es ruido visual, no plata mal contada. Borrar las 13 filas
--  deja la caja con $48.000 de MÁS.
--
--  Por eso hay dos variantes. La A es la que casi seguro quieres.
-- ============================================================================

\set compra_id 854

-- ############################################################################
-- ## PASO 0 — INSPECCIÓN (solo lectura, corre esto primero y léelo)
-- ############################################################################

-- 0.1 ¿Qué hay en caja y cuánto netea?
SELECT tipo, count(*) AS filas, sum(monto) AS suma
FROM movimiento_caja
WHERE origen_tipo = 'COMPRA' AND origen_id = :compra_id
GROUP BY tipo;

SELECT id, tipo, monto, fecha, turno_caja_id, metodo_pago, created_at
FROM movimiento_caja
WHERE origen_tipo = 'COMPRA' AND origen_id = :compra_id
ORDER BY id;

-- 0.2 Neto real. Si esto da -48000, la caja está correcta y va la VARIANTE A.
SELECT sum(CASE WHEN tipo = 'EGRESO' THEN -monto ELSE monto END) AS neto_caja
FROM movimiento_caja
WHERE origen_tipo = 'COMPRA' AND origen_id = :compra_id;

-- 0.3 Asientos contables
SELECT id, tipo_origen, estado, numero_comprobante, fecha,
       total_debito, total_credito, periodo_contable_id
FROM asiento_contable
WHERE origen_id = :compra_id
  AND tipo_origen IN ('COMPRA', 'ANULACION_COMPRA')
ORDER BY id;

-- 0.4 Comprobante de egreso (debe ser UNO solo — se conserva al editar)
SELECT id, numero_comprobante, tipo, monto, anulado, turno_caja_id, created_at
FROM comprobante_caja
WHERE origen = 'COMPRA' AND origen_id = :compra_id
ORDER BY id;

-- 0.5 GUARDA 1 — ¿hay turnos CERRADOS entre los movimientos a borrar?
--     Los estados cerrados son DOS: 'CERRADA' y 'CERRADA_AUTO'.
--     Si alguno sale como 'CERRADO — OJO', borrar altera un arqueo ya
--     cuadrado y firmado: el turno cierra con un total que dejará de
--     poder reconstruirse desde sus movimientos.
SELECT DISTINCT t.id AS turno_id, t.estado,
       CASE WHEN t.estado IN ('CERRADA','CERRADA_AUTO')
            THEN 'CERRADO — OJO' ELSE 'abierto, sin problema' END AS alerta
FROM movimiento_caja m
JOIN turno_caja t ON t.id = m.turno_caja_id
WHERE m.origen_tipo = 'COMPRA' AND m.origen_id = :compra_id
ORDER BY 1;

-- 0.6 GUARDA 2 — ¿hay períodos contables CERRADOS entre los asientos a borrar?
--     Si sale algo, NO borres esos asientos: la ley no lo permite.
SELECT DISTINCT p.id AS periodo_id, p.estado
FROM asiento_contable a
JOIN periodo_contable p ON p.id = a.periodo_contable_id
WHERE a.origen_id = :compra_id
  AND a.tipo_origen IN ('COMPRA', 'ANULACION_COMPRA');

-- 0.7 GUARDA 3 — FKs NO ACTION que harían fallar el DELETE
SELECT 'devolucion' AS tabla, count(*) AS refs
FROM devolucion d
WHERE d.movimiento_caja_id IN (
    SELECT id FROM movimiento_caja
    WHERE origen_tipo = 'COMPRA' AND origen_id = :compra_id)
UNION ALL
SELECT 'depreciacion_periodo', count(*)
FROM depreciacion_periodo dp
WHERE dp.asiento_id IN (
    SELECT id FROM asiento_contable
    WHERE origen_id = :compra_id
      AND tipo_origen IN ('COMPRA', 'ANULACION_COMPRA'));


-- ############################################################################
-- ## VARIANTE A — colapsar el ruido, CONSERVAR el neto   ← RECOMENDADA
-- ##
-- ## Borra los 6 pares reverso/egreso y deja vivo el último EGRESO.
-- ## Saldo de caja: NO CAMBIA. Contabilidad: NO CAMBIA.
-- ## Úsala si la compra #854 sigue siendo válida.
-- ############################################################################

BEGIN;

-- A.1 Caja: fuera todos los INGRESO (reversos) y todos los EGRESO menos el último.
DELETE FROM movimiento_caja
WHERE origen_tipo = 'COMPRA'
  AND origen_id = :compra_id
  AND id <> (
      SELECT max(id) FROM movimiento_caja
      WHERE origen_tipo = 'COMPRA' AND origen_id = :compra_id AND tipo = 'EGRESO');

-- A.2 Contabilidad: fuera los asientos ANULADOS y sus contraasientos.
--     El CONTABILIZADO (el vigente) se queda. asiento_detalle cae por CASCADE.
--     Se excluyen los de período CERRADO por si acaso.
DELETE FROM asiento_contable a
WHERE a.origen_id = :compra_id
  AND (
        (a.tipo_origen = 'COMPRA'            AND a.estado = 'ANULADO')
     OR (a.tipo_origen = 'ANULACION_COMPRA')
      )
  AND NOT EXISTS (
      SELECT 1 FROM periodo_contable p
      WHERE p.id = a.periodo_contable_id AND p.estado = 'CERRADO');

-- A.3 VERIFICAR ANTES DE CONFIRMAR
SELECT 'caja' AS que, count(*) AS filas,
       sum(CASE WHEN tipo = 'EGRESO' THEN -monto ELSE monto END) AS neto
FROM movimiento_caja
WHERE origen_tipo = 'COMPRA' AND origen_id = :compra_id;
-- Esperado: 1 fila, neto -48000

SELECT 'asientos' AS que, tipo_origen, estado, count(*)
FROM asiento_contable
WHERE origen_id = :compra_id AND tipo_origen IN ('COMPRA','ANULACION_COMPRA')
GROUP BY tipo_origen, estado;
-- Esperado: 1 fila COMPRA / CONTABILIZADO

ROLLBACK;   -- <<< cambia a COMMIT cuando los números de arriba te cuadren


-- ############################################################################
-- ## VARIANTE B — borrar TODO rastro de pago de la compra #854
-- ##
-- ## Solo si la compra está ANULADA o la vas a eliminar. Esto SÍ mueve el
-- ## saldo: la caja sube $48.000 porque desaparece el egreso legítimo.
-- ## Si la compra sigue viva, esto te descuadra el arqueo.
-- ############################################################################

-- BEGIN;
--
-- DELETE FROM movimiento_caja
-- WHERE origen_tipo = 'COMPRA' AND origen_id = :compra_id;
--
-- DELETE FROM asiento_contable
-- WHERE origen_id = :compra_id
--   AND tipo_origen IN ('COMPRA', 'ANULACION_COMPRA');
--
-- DELETE FROM comprobante_caja
-- WHERE origen = 'COMPRA' AND origen_id = :compra_id;
--
-- UPDATE compra_pago SET activo = false WHERE compra_id = :compra_id;
--
-- SELECT (SELECT count(*) FROM movimiento_caja
--         WHERE origen_tipo='COMPRA' AND origen_id = :compra_id) AS movs,
--        (SELECT count(*) FROM asiento_contable
--         WHERE origen_id = :compra_id
--           AND tipo_origen IN ('COMPRA','ANULACION_COMPRA')) AS asientos,
--        (SELECT count(*) FROM comprobante_caja
--         WHERE origen='COMPRA' AND origen_id = :compra_id) AS comprobantes;
-- -- Esperado: 0, 0, 0
--
-- ROLLBACK;   -- <<< cambia a COMMIT
