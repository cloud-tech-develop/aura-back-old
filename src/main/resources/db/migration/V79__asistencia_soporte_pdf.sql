-- ── V79: Soporte PDF de asistencia (Fase C) ─────────────────────────────────
-- El líder sube el PDF firmado/escaneado como soporte de la asistencia del frente.
-- Se guarda en Cloudflare R2 (archivo_url) con su hash para detectar duplicados.

CREATE TABLE IF NOT EXISTS asistencia_soporte_pdf (
    id                    BIGSERIAL     PRIMARY KEY,
    empresa_id            INT           NOT NULL REFERENCES empresa(id),
    asistencia_frente_id  BIGINT        REFERENCES asistencia_frente(id) ON DELETE CASCADE,
    plantilla_id          BIGINT,
    proyecto_id           BIGINT,
    frente_id             BIGINT,
    lider_id              BIGINT,
    fecha                 DATE          NOT NULL,
    archivo_url           VARCHAR(500)  NOT NULL,
    nombre_archivo        VARCHAR(255),
    peso_archivo          BIGINT,
    mime_type             VARCHAR(100),
    hash_archivo          VARCHAR(80),
    estado                VARCHAR(20)   NOT NULL DEFAULT 'CARGADO',
    observacion           VARCHAR(255),
    created_by            BIGINT,
    updated_by            BIGINT,
    deleted_by            BIGINT,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP,

    CONSTRAINT chk_soporte_pdf_estado CHECK (estado IN
        ('CARGADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO', 'ANULADO'))
);

CREATE INDEX IF NOT EXISTS idx_soporte_pdf_frente ON asistencia_soporte_pdf(frente_id, fecha);
CREATE INDEX IF NOT EXISTS idx_soporte_pdf_asis   ON asistencia_soporte_pdf(asistencia_frente_id);
CREATE INDEX IF NOT EXISTS idx_soporte_pdf_hash   ON asistencia_soporte_pdf(empresa_id, hash_archivo);
