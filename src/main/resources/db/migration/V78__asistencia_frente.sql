-- ── V78: Captura de asistencia por frente (Fase B) ──────────────────────────
-- Cabecera por frente-fecha + detalle por trabajador + alertas antifraude.
-- plantilla_id / soporte_pdf_id quedan nullable (se usan en Fase C - PDF).

CREATE TABLE IF NOT EXISTS asistencia_frente (
    id                    BIGSERIAL     PRIMARY KEY,
    empresa_id            INT           NOT NULL REFERENCES empresa(id),
    proyecto_id           BIGINT        NOT NULL REFERENCES proyecto(id),
    frente_id             BIGINT        NOT NULL REFERENCES proyecto_frente(id),
    plantilla_id          BIGINT,
    soporte_pdf_id        BIGINT,
    lider_id              BIGINT,
    fecha                 DATE          NOT NULL,
    estado                VARCHAR(30)   NOT NULL DEFAULT 'BORRADOR',
    observacion_lider     VARCHAR(500),
    observacion_admin     VARCHAR(500),
    enviado_revision_at   TIMESTAMP,
    aprobado_por          BIGINT,
    aprobado_at           TIMESTAMP,
    rechazado_por         BIGINT,
    rechazado_at          TIMESTAMP,
    created_by            BIGINT,
    updated_by            BIGINT,
    deleted_by            BIGINT,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP,

    CONSTRAINT chk_asis_frente_estado CHECK (estado IN
        ('BORRADOR', 'ENVIADO_REVISION', 'EN_CORRECCION', 'APROBADO', 'RECHAZADO', 'ENVIADO_NOMINA', 'ANULADO'))
);

CREATE INDEX IF NOT EXISTS idx_asis_frente_empresa  ON asistencia_frente(empresa_id);
CREATE INDEX IF NOT EXISTS idx_asis_frente_frente   ON asistencia_frente(frente_id, fecha);
CREATE UNIQUE INDEX IF NOT EXISTS ux_asis_frente_fecha
    ON asistencia_frente(frente_id, fecha) WHERE deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS asistencia_frente_detalle (
    id                     BIGSERIAL     PRIMARY KEY,
    empresa_id             INT           NOT NULL REFERENCES empresa(id),
    asistencia_frente_id   BIGINT        NOT NULL REFERENCES asistencia_frente(id) ON DELETE CASCADE,
    proyecto_id            BIGINT        NOT NULL,
    frente_id              BIGINT        NOT NULL,
    empleado_id            BIGINT        NOT NULL REFERENCES empleados(id),
    fecha                  DATE          NOT NULL,
    hora_entrada           TIME,
    hora_salida            TIME,
    horas_trabajadas       NUMERIC(6,2)  NOT NULL DEFAULT 0,
    horas_ordinarias       NUMERIC(6,2)  NOT NULL DEFAULT 0,
    horas_extra_diurnas    NUMERIC(6,2)  NOT NULL DEFAULT 0,
    horas_extra_nocturnas  NUMERIC(6,2)  NOT NULL DEFAULT 0,
    horas_dominicales      NUMERIC(6,2)  NOT NULL DEFAULT 0,
    horas_festivas         NUMERIC(6,2)  NOT NULL DEFAULT 0,
    estado_asistencia      VARCHAR(20)   NOT NULL DEFAULT 'SIN_REGISTRO',
    estado_revision        VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    observacion_lider      VARCHAR(255),
    observacion_admin      VARCHAR(255),
    aprobado_por           BIGINT,
    aprobado_at            TIMESTAMP,
    created_by             BIGINT,
    updated_by             BIGINT,
    deleted_by             BIGINT,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at             TIMESTAMP,

    CONSTRAINT chk_asis_det_asistencia CHECK (estado_asistencia IN
        ('ASISTIO', 'NO_ASISTIO', 'LLEGO_TARDE', 'SALIO_TEMPRANO', 'PERMISO', 'INCAPACIDAD', 'VACACIONES', 'SUSPENDIDO', 'SIN_REGISTRO')),
    CONSTRAINT chk_asis_det_revision CHECK (estado_revision IN
        ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'AJUSTADO', 'ENVIADO_NOMINA'))
);

CREATE INDEX IF NOT EXISTS idx_asis_det_frente    ON asistencia_frente_detalle(asistencia_frente_id);
CREATE INDEX IF NOT EXISTS idx_asis_det_empleado  ON asistencia_frente_detalle(empleado_id, fecha);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS asistencia_alerta (
    id                            BIGSERIAL    PRIMARY KEY,
    empresa_id                    INT          NOT NULL REFERENCES empresa(id),
    asistencia_frente_id          BIGINT       REFERENCES asistencia_frente(id) ON DELETE CASCADE,
    asistencia_frente_detalle_id  BIGINT,
    proyecto_id                   BIGINT,
    frente_id                     BIGINT,
    empleado_id                   BIGINT,
    tipo_alerta                   VARCHAR(40)  NOT NULL,
    nivel                         VARCHAR(15)  NOT NULL DEFAULT 'ADVERTENCIA',
    descripcion                   VARCHAR(255),
    estado                        VARCHAR(15)  NOT NULL DEFAULT 'ABIERTA',
    created_by                    BIGINT,
    updated_by                    BIGINT,
    deleted_by                    BIGINT,
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                    TIMESTAMP,

    CONSTRAINT chk_asis_alerta_nivel  CHECK (nivel IN ('INFO', 'ADVERTENCIA', 'CRITICA')),
    CONSTRAINT chk_asis_alerta_estado CHECK (estado IN ('ABIERTA', 'REVISADA', 'RESUELTA', 'IGNORADA'))
);

CREATE INDEX IF NOT EXISTS idx_asis_alerta_frente ON asistencia_alerta(asistencia_frente_id);
