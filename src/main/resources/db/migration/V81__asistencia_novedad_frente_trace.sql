-- ── V81: Trazabilidad de novedades generadas desde asistencia por FRENTE (Fase E) ──
-- Reutilizamos la staging asistencia_novedad_nomina con origen = 'PROYECTO_FRENTE'.
-- Se agregan columnas de traza al proyecto/frente/asistencia que originó la novedad.

ALTER TABLE asistencia_novedad_nomina
    ADD COLUMN IF NOT EXISTS proyecto_id                  BIGINT,
    ADD COLUMN IF NOT EXISTS frente_id                    BIGINT,
    ADD COLUMN IF NOT EXISTS asistencia_frente_id         BIGINT,
    ADD COLUMN IF NOT EXISTS asistencia_frente_detalle_id BIGINT,
    ADD COLUMN IF NOT EXISTS soporte_pdf_id               BIGINT;

CREATE INDEX IF NOT EXISTS idx_asis_nov_frente
    ON asistencia_novedad_nomina(asistencia_frente_id);
