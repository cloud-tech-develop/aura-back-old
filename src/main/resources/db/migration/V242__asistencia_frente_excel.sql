-- V242 — Marcaje desde una plantilla de Excel del mes.
--
-- minutos_descanso: el descanso real del día (almuerzo, pausas). Antes solo
--   existía el del turno del frente; con el Excel cada día trae el suyo. NULL =
--   se usa el del turno, como hasta ahora.
-- origen: DIGITACION (pantalla día por día) | EXCEL (plantilla del mes). La
--   asistencia importada tiene como soporte el mismo Excel subido.
--
-- Idempotente.

ALTER TABLE asistencia_frente_detalle ADD COLUMN IF NOT EXISTS minutos_descanso INT;

ALTER TABLE asistencia_frente ADD COLUMN IF NOT EXISTS origen VARCHAR(20) NOT NULL DEFAULT 'DIGITACION';
