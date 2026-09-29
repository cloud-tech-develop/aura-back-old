-- ── V70: Asistencia Fase 3 — incidencias y período de asistencia ─────────────

-- Período de asistencia (ventana de revisión previa a la nómina)
CREATE TABLE periodo_asistencia (
    id                  BIGSERIAL   PRIMARY KEY,
    empresa_id          INT         NOT NULL REFERENCES empresa(id),
    periodo_nomina_id   BIGINT      REFERENCES periodo_nomina(id),
    fecha_inicio        DATE        NOT NULL,
    fecha_fin           DATE        NOT NULL,
    estado              VARCHAR(30) NOT NULL DEFAULT 'ABIERTO',
                        -- ABIERTO | EN_REVISION | APROBADO | BLOQUEADO | ENVIADO_A_NOMINA | ANULADO
    creado_por          INT,
    fecha_creacion      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cerrado_por         INT,
    fecha_cierre        TIMESTAMP,
    aprobado_por        INT,
    fecha_aprobacion    TIMESTAMP,

    CONSTRAINT chk_periodo_asistencia_estado
        CHECK (estado IN ('ABIERTO', 'EN_REVISION', 'APROBADO', 'BLOQUEADO', 'ENVIADO_A_NOMINA', 'ANULADO'))
);

CREATE INDEX idx_periodo_asistencia_empresa ON periodo_asistencia(empresa_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Incidencias de asistencia (toda diferencia/error/ausencia detectada)
CREATE TABLE asistencia_incidencia (
    id                   BIGSERIAL   PRIMARY KEY,
    empresa_id           INT         NOT NULL REFERENCES empresa(id),
    asistencia_dia_id    BIGINT      REFERENCES asistencia_dia(id) ON DELETE CASCADE,
    empleado_id          BIGINT      NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    fecha                DATE        NOT NULL,
    tipo_incidencia      VARCHAR(40) NOT NULL,
                         -- NO_MARCO_ENTRADA | NO_MARCO_SALIDA | LLEGADA_TARDE | SALIDA_TEMPRANA
                         -- AUSENCIA_DIA_COMPLETO | HORAS_EXTRA_PENDIENTES_APROBACION
                         -- TURNO_NO_ASIGNADO | MARCACION_DUPLICADA | MARCACION_INCONSISTENTE | MARCACION_MANUAL
    descripcion          VARCHAR(255),
    estado               VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_REVISION',
                         -- PENDIENTE_REVISION | JUSTIFICADA | NO_JUSTIFICADA
                         -- APROBADA_COMO_NOVEDAD | RECHAZADA | CORREGIDA | ANULADA
    requiere_soporte     BOOLEAN     NOT NULL DEFAULT FALSE,
    soporte_url          VARCHAR(255),
    registrado_por       INT,
    fecha_registro       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revisado_por         INT,
    fecha_revision       TIMESTAMP,
    observacion_revision VARCHAR(255),

    CONSTRAINT chk_incidencia_estado
        CHECK (estado IN ('PENDIENTE_REVISION', 'JUSTIFICADA', 'NO_JUSTIFICADA',
                          'APROBADA_COMO_NOVEDAD', 'RECHAZADA', 'CORREGIDA', 'ANULADA'))
);

CREATE INDEX idx_incidencia_empresa      ON asistencia_incidencia(empresa_id);
CREATE INDEX idx_incidencia_empleado_fec ON asistencia_incidencia(empleado_id, fecha);
CREATE INDEX idx_incidencia_dia          ON asistencia_incidencia(asistencia_dia_id);
