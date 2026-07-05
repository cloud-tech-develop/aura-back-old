-- ── V83: Clasificación de horas en el detalle de asistencia (G2) ────────────
-- Columnas granulares que llena el motor de clasificación al digitar.

ALTER TABLE asistencia_frente_detalle
    ADD COLUMN IF NOT EXISTS horas_ordinarias_diurnas        NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS horas_ordinarias_nocturnas      NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS horas_dominicales_festivas      NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS horas_extra_diurnas_dom_fest    NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS horas_extra_nocturnas_dom_fest  NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valor_hora_base                 NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS valor_calculado_estimado        NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS requiere_revision               BOOLEAN NOT NULL DEFAULT FALSE;
