-- ============================================================================
--  Poner diferencia = 0 en los turnos 710, 703 y 698. Solo esos tres.
--
--  CONTEXTO
--  El listado de turnos lee t.diferencia directo cuando el estado es CERRADA
--  o CERRADA_AUTO, y nada la recalcula después del cierre: lo que se escriba
--  aquí es lo que se ve, y se queda.
--
--  Hay TRES columnas y el detalle del turno no lee la misma que el listado:
--    diferencia           -> la del cierre. Es la que muestra el LISTADO.
--    diferencia_original  -> copia de la anterior, se llena SOLO al primer
--                            ajuste retroactivo. El DETALLE la prefiere sobre
--                            `diferencia` si no está en NULL.
--    diferencia_ajustada  -> recalculada tras los ajustes. La muestra el DETALLE.
--  Si las dos últimas están en NULL (lo normal si nunca se ajustó el turno),
--  basta con tocar `diferencia`. El PASO 0 te lo dice.
-- ============================================================================

-- ############################################################################
-- ## PASO 0 — ANTES (solo lectura). Guarda esta salida.
-- ############################################################################

SELECT id, estado, base_inicial, total_efectivo_sistema, total_efectivo_real,
       diferencia, diferencia_original, diferencia_ajustada
FROM turno_caja
WHERE id IN (710, 703, 698)
ORDER BY id DESC;
-- Valores esperados hoy:
--   710 -> diferencia    170000.27
--   703 -> diferencia   2908844.00
--   698 -> diferencia   2690125.66
-- Si diferencia_original / diferencia_ajustada salen NULL, el PASO 1 basta.
-- Si salen con valor, descomenta las dos líneas marcadas OPCIONAL.


-- ############################################################################
-- ## PASO 1 — ACTUALIZAR
-- ############################################################################

BEGIN;

UPDATE turno_caja
SET diferencia = 0
    -- OPCIONAL: descomenta solo si el PASO 0 las mostró CON valor.
    -- , diferencia_ajustada = CASE WHEN diferencia_ajustada IS NULL
    --                              THEN NULL ELSE 0 END
    -- , diferencia_original = CASE WHEN diferencia_original IS NULL
    --                              THEN NULL ELSE 0 END
WHERE id IN (710, 703, 698);
-- Esperado: UPDATE 3
-- Si dice otra cosa que no sea 3 -> ROLLBACK.

-- ── VERIFICACIÓN ────────────────────────────────────────────────────────────
SELECT id, estado, diferencia, diferencia_original, diferencia_ajustada
FROM turno_caja
WHERE id IN (710, 703, 698)
ORDER BY id DESC;
-- Esperado: las 3 con diferencia = 0.00

-- CONTROL: ningún otro turno quedó tocado. Cuenta cuántos turnos tienen
-- diferencia exactamente 0 — antes de esto solo podían ser los que ya la
-- tuvieran. Si el número te sorprende, ROLLBACK.
SELECT count(*) AS turnos_en_cero FROM turno_caja WHERE diferencia = 0;

ROLLBACK;   -- <<<<<<<<<< CÁMBIALO A  COMMIT;  <<<<<<<<<<
