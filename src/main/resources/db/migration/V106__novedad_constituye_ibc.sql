-- ── V106: Fase 0 — marcar qué novedades constituyen IBC ─────────────────────
--
-- BUG QUE ARREGLA (B2): hoy `calcular()` suma indiscriminadamente todas las
-- novedades no-deducción en `novedadesDevengadas`, y esa suma entra a la base
-- de seguridad social. Pero:
--   · HORA_EXTRA_*, COMISION  → SÍ son base de seguridad social
--   · BONO no salarial        → NO lo es
--   · INCAPACIDAD, LICENCIA   → tienen tratamiento propio
--
-- Sin esta marca, el IBC sale inflado o deflactado según el tipo de novedad.
--
-- Contexto: nómina no la usa nadie todavía → no hay novedades cargadas, así
-- que el backfill es sobre tabla vacía. Se define bien desde cero.
--
-- Seguro sin tocar código: agrega columna con DEFAULT.

ALTER TABLE nomina_novedad
    ADD COLUMN IF NOT EXISTS constituye_ibc BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN nomina_novedad.constituye_ibc IS
    'Si el valor entra en la base de seguridad social. Horas extra y comisiones SÍ; '
    'bonos no salariales NO. Ver Fase 0 del PLAN_MIGRACION_NOMINA.md.';

-- ── Backfill por tipo ───────────────────────────────────────────────────────
-- ⚠️ DECISIÓN DE NEGOCIO: `BONO` se marca como NO salarial.
--    Es lo habitual (bono no constitutivo de salario pactado como tal), pero
--    si algún cliente paga bonos salariales, hay que discriminarlos.
--    Hoy no hay datos → la decisión aplica solo hacia adelante.
UPDATE nomina_novedad
   SET constituye_ibc = FALSE
 WHERE tipo IN (
        'BONO',                 -- no salarial (ver advertencia arriba)
        'INCAPACIDAD',          -- la paga EPS/ARL, no es salario
        'LICENCIA_REMUNERADA',
        'PRESTAMO',             -- deducción
        'EMBARGO',              -- deducción
        'OTRO_DESCUENTO'        -- deducción
       );

-- Las que SÍ son base (quedan en TRUE por el default, explícito para que se lea):
--   HORA_EXTRA_DIURNA, HORA_EXTRA_NOCTURNA, HORA_EXTRA_DOMINICAL,
--   HORA_EXTRA_FESTIVO, COMISION, OTRO_DEVENGO


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTA PARA EL MOTOR
--
-- El cálculo debe pasar de:
--     totalDevengado = salarioProporcional + auxilio + novedadesDevengadas
--     deduccionSalud = porcentaje(totalDevengado, pct)          ← MAL
--
-- a:
--     baseIbc = salarioProporcional
--             + SUM(novedades WHERE constituye_ibc = TRUE AND NOT es_deduccion)
--     -- SIN auxilio de transporte: no es base de seguridad social (bug B1)
--     deduccionSalud = porcentaje(baseIbc, pct)                 ← BIEN
--
-- `totalDevengado` sigue existiendo (es lo que se le paga al empleado), pero
-- deja de ser la base de seguridad social. Son dos cosas distintas.
-- ═══════════════════════════════════════════════════════════════════════════
