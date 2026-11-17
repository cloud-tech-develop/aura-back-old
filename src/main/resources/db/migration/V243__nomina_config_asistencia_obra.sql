-- V243 — Cómo trabaja la empresa la asistencia por obra (Configuración de nómina).
--
-- asistencia_usa_frentes: FALSE = la empresa trabaja por proyecto sin dividirlo en
--   frentes; todo va al frente General del proyecto (V241) y no se crean frentes.
-- asistencia_exige_soporte: FALSE = un día se puede enviar a revisión sin soporte
--   (PDF firmado o el Excel importado).
-- asistencia_registro: cómo se registran las horas —
--   MANUAL (digitación día por día) | EXCEL (solo la plantilla del mes) | AMBOS.
--
-- Por defecto se conserva lo de hoy: frentes, soporte obligatorio y ambas formas.
-- Idempotente.

ALTER TABLE nomina_config ADD COLUMN IF NOT EXISTS asistencia_usa_frentes BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE nomina_config ADD COLUMN IF NOT EXISTS asistencia_exige_soporte BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE nomina_config ADD COLUMN IF NOT EXISTS asistencia_registro VARCHAR(12) NOT NULL DEFAULT 'AMBOS';

ALTER TABLE nomina_config DROP CONSTRAINT IF EXISTS chk_nc_asistencia_registro;

ALTER TABLE nomina_config ADD CONSTRAINT chk_nc_asistencia_registro
    CHECK (asistencia_registro IN ('MANUAL', 'EXCEL', 'AMBOS'));
