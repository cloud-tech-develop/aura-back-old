-- ── V68: Asistencia Fase 1 — turnos de trabajo y asignación ──────────────────

-- Turnos de trabajo (maestro por empresa)
CREATE TABLE turno_trabajo (
    id                        BIGSERIAL   PRIMARY KEY,
    empresa_id                INT         NOT NULL REFERENCES empresa(id),
    nombre                    VARCHAR(80) NOT NULL,
    hora_inicio               TIME        NOT NULL,
    hora_fin                  TIME        NOT NULL,
    minutos_descanso          INT         NOT NULL DEFAULT 0,
    tolera_llegada_tarde_min  INT         NOT NULL DEFAULT 0,
    cruza_medianoche          BOOLEAN     NOT NULL DEFAULT FALSE,
    activo                    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_turno_trabajo_empresa ON turno_trabajo(empresa_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Asignación de turno a empleado (con historia por vigencia)
CREATE TABLE empleado_turno (
    id            BIGSERIAL   PRIMARY KEY,
    empresa_id    INT         NOT NULL REFERENCES empresa(id),
    empleado_id   BIGINT      NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    turno_id      BIGINT      NOT NULL REFERENCES turno_trabajo(id),
    fecha_inicio  DATE        NOT NULL,
    fecha_fin     DATE,
    dias_semana   VARCHAR(30) NOT NULL DEFAULT '1,2,3,4,5',
                  -- días ISO: 1=Lun ... 7=Dom (CSV)
    activo        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_empleado_turno_empresa  ON empleado_turno(empresa_id);
CREATE INDEX idx_empleado_turno_empleado ON empleado_turno(empleado_id);
CREATE INDEX idx_empleado_turno_vigencia ON empleado_turno(empleado_id, fecha_inicio, fecha_fin);
