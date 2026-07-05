-- ── V80: Traza de revisión/aprobación de asistencia (Fase D) ────────────────
-- Cada aprobación, rechazo, solicitud de corrección o ajuste queda registrado.

CREATE TABLE IF NOT EXISTS asistencia_frente_aprobacion (
    id                            BIGSERIAL    PRIMARY KEY,
    empresa_id                    INT          NOT NULL REFERENCES empresa(id),
    asistencia_frente_id          BIGINT       NOT NULL REFERENCES asistencia_frente(id) ON DELETE CASCADE,
    asistencia_frente_detalle_id  BIGINT,
    administrador_id              BIGINT,
    accion                        VARCHAR(30)  NOT NULL,
    valor_anterior                VARCHAR(255),
    valor_aprobado                VARCHAR(255),
    observacion                   VARCHAR(500),
    created_by                    BIGINT,
    updated_by                    BIGINT,
    deleted_by                    BIGINT,
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                    TIMESTAMP,

    CONSTRAINT chk_asis_aprob_accion CHECK (accion IN
        ('APROBAR', 'RECHAZAR', 'SOLICITAR_CORRECCION', 'AJUSTAR', 'ANULAR'))
);

CREATE INDEX IF NOT EXISTS idx_asis_aprob_frente ON asistencia_frente_aprobacion(asistencia_frente_id);
