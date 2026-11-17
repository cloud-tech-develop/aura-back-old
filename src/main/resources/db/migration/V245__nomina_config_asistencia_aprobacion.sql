-- V245 — ¿La asistencia por obra la aprueba un supervisor antes de la nómina?
--
-- TRUE (lo de siempre): cada día pasa por enviar a revisión → aprobar → enviar a
--   nómina, y la prenómina solo toma los días enviados a nómina.
-- FALSE: no hay revisión. Al liquidar la prenómina, el sistema toma los días
--   registrados del empleado (borrador o en revisión), los aprueba y los manda a
--   nómina solo; las horas extra y recargos entran a la liquidación.
-- Idempotente.

ALTER TABLE nomina_config ADD COLUMN IF NOT EXISTS asistencia_requiere_aprobacion BOOLEAN NOT NULL DEFAULT TRUE;
