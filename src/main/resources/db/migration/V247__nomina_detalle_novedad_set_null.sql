-- V247 — Reliquidar ya no choca con las líneas de la liquidación anterior.
--
-- nomina_detalle.novedad_id (V105) es solo el rastro de qué novedad produjo una
-- línea. Al reliquidar, el sistema quita las novedades de asistencia, vacaciones
-- o retroactivo para volver a generarlas, y las líneas viejas todavía las
-- referenciaban: "update o delete en nomina_novedad viola la llave foránea
-- nomina_detalle_novedad_id_fkey". Lo mismo al quitar una novedad manual de un
-- empleado ya liquidado. Las líneas se reescriben enteras en cada liquidación,
-- así que basta con que pierdan la referencia (ON DELETE SET NULL).
--
-- Idempotente.

ALTER TABLE nomina_detalle DROP CONSTRAINT IF EXISTS nomina_detalle_novedad_id_fkey;

ALTER TABLE nomina_detalle
    ADD CONSTRAINT nomina_detalle_novedad_id_fkey
    FOREIGN KEY (novedad_id) REFERENCES nomina_novedad(id) ON DELETE SET NULL;
