-- ── V69: Asistencia Fase 2 — marcajes y consolidación diaria ─────────────────

-- Marcajes crudos de asistencia (entrada/salida/descansos)
CREATE TABLE asistencia_marcaje (
    id                  BIGSERIAL    PRIMARY KEY,
    empresa_id          INT          NOT NULL REFERENCES empresa(id),
    empleado_id         BIGINT       NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    fecha               DATE         NOT NULL,
    fecha_hora_marcaje  TIMESTAMP    NOT NULL,
    tipo_marcaje        VARCHAR(20)  NOT NULL,
                        -- ENTRADA | SALIDA | INICIO_DESCANSO | FIN_DESCANSO
    origen_marcaje      VARCHAR(20)  NOT NULL DEFAULT 'ASISTENTE',
                        -- EMPLEADO | ASISTENTE | ADMIN | SUPERVISOR | BIOMETRICO | IMPORTADO_EXCEL | APP_MOVIL
    registrado_por      INT,
    observacion         VARCHAR(255),
    evidencia_url       VARCHAR(255),
    estado              VARCHAR(20)  NOT NULL DEFAULT 'VALIDO',
                        -- VALIDO | PENDIENTE_REVISION | ANULADO | CORREGIDO
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_marcaje_tipo
        CHECK (tipo_marcaje IN ('ENTRADA', 'SALIDA', 'INICIO_DESCANSO', 'FIN_DESCANSO')),
    CONSTRAINT chk_marcaje_estado
        CHECK (estado IN ('VALIDO', 'PENDIENTE_REVISION', 'ANULADO', 'CORREGIDO'))
);

CREATE INDEX idx_marcaje_empresa       ON asistencia_marcaje(empresa_id);
CREATE INDEX idx_marcaje_empleado_dia  ON asistencia_marcaje(empleado_id, fecha);

-- ─────────────────────────────────────────────────────────────────────────────

-- Asistencia diaria consolidada (una fila por empleado-día)
CREATE TABLE asistencia_dia (
    id                          BIGSERIAL   PRIMARY KEY,
    empresa_id                  INT         NOT NULL REFERENCES empresa(id),
    empleado_id                 BIGINT      NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    fecha                       DATE        NOT NULL,
    turno_id                    BIGINT      REFERENCES turno_trabajo(id),
    hora_entrada_programada     TIME,
    hora_salida_programada      TIME,
    hora_entrada_real           TIME,
    hora_salida_real            TIME,
    minutos_programados         INT         NOT NULL DEFAULT 0,
    minutos_trabajados          INT         NOT NULL DEFAULT 0,
    minutos_tarde               INT         NOT NULL DEFAULT 0,
    minutos_salida_temprana     INT         NOT NULL DEFAULT 0,
    minutos_extra_diurna        INT         NOT NULL DEFAULT 0,
    minutos_extra_nocturna      INT         NOT NULL DEFAULT 0,
    minutos_dominical_festiva   INT         NOT NULL DEFAULT 0,
    minutos_nocturnos           INT         NOT NULL DEFAULT 0,
    estado_asistencia           VARCHAR(30) NOT NULL DEFAULT 'SIN_MARCAJE_COMPLETO',
    estado_aprobacion           VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    aprobado_por                INT,
    fecha_aprobacion            TIMESTAMP,
    observacion                 VARCHAR(255),
    created_at                  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_asistencia_dia_empleado_fecha UNIQUE (empleado_id, fecha),
    CONSTRAINT chk_asistencia_dia_aprob
        CHECK (estado_aprobacion IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'AJUSTADO', 'BLOQUEADO', 'ENVIADO_A_NOMINA'))
);

CREATE INDEX idx_asistencia_dia_empresa      ON asistencia_dia(empresa_id);
CREATE INDEX idx_asistencia_dia_empleado_fec ON asistencia_dia(empleado_id, fecha);
