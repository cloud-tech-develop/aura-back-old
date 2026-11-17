-- V240 — Autorizar una novedad que pasa un tope.
--
-- Hasta aquí "requiere autorización" en los topes del cargo y de la vinculación
-- no se podía cumplir: no había cómo aprobar una novedad de nómina, y pasar el
-- tope de horas extra bloqueaba siempre. Ahora la novedad que pasa un tope queda
-- PENDIENTE ("por autorizar") y alguien la autoriza (APROBADA). Aquí se guarda
-- quién y cuándo. Idempotente.

ALTER TABLE nomina_novedad ADD COLUMN IF NOT EXISTS autorizada_por BIGINT;

ALTER TABLE nomina_novedad ADD COLUMN IF NOT EXISTS autorizada_at TIMESTAMP;
