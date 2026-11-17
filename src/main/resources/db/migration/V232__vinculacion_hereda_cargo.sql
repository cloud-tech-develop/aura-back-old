-- V232 — La vinculación hereda la plantilla de su cargo (docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md, F6).
--
-- Herencia viva: el motor toma los conceptos y novedades del cargo al liquidar.
-- La vinculación solo guarda excepciones: una fila propia del mismo concepto la
-- reemplaza, y una fila con excluido = TRUE la quita para ese empleado.
--
-- Solo agrega columnas.

ALTER TABLE vinculacion_concepto ADD COLUMN IF NOT EXISTS excluido BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE vinculacion_novedad_permitida ADD COLUMN IF NOT EXISTS excluido BOOLEAN NOT NULL DEFAULT FALSE;
