-- ── V77: Módulo Proyectos y Frentes (Fase A) ────────────────────────────────
-- Capa previa a la asistencia por obra. Convive con la asistencia por turnos.
-- Todas las tablas con borrado lógico (deleted_at) y auditoría (created_by/...).

CREATE TABLE IF NOT EXISTS proyecto (
    id                             BIGSERIAL     PRIMARY KEY,
    empresa_id                     INT           NOT NULL REFERENCES empresa(id),
    codigo                         VARCHAR(30)   NOT NULL,
    nombre                         VARCHAR(150)  NOT NULL,
    cliente_id                     BIGINT,
    descripcion                    TEXT,
    fecha_inicio                   DATE,
    fecha_fin                      DATE,
    estado                         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
    centro_costo_id                BIGINT,
    responsable_administrativo_id  BIGINT,
    requiere_control_asistencia    BOOLEAN       NOT NULL DEFAULT TRUE,
    ciudad                         VARCHAR(100),
    ubicacion                      VARCHAR(200),
    observacion                    TEXT,
    created_by                     BIGINT,
    updated_by                     BIGINT,
    deleted_by                     BIGINT,
    created_at                     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                     TIMESTAMP,

    CONSTRAINT chk_proyecto_estado CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'FINALIZADO', 'ANULADO'))
);

CREATE INDEX IF NOT EXISTS idx_proyecto_empresa ON proyecto(empresa_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_proyecto_empresa_codigo ON proyecto(empresa_id, codigo) WHERE deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS proyecto_frente (
    id             BIGSERIAL     PRIMARY KEY,
    empresa_id     INT           NOT NULL REFERENCES empresa(id),
    proyecto_id    BIGINT        NOT NULL REFERENCES proyecto(id),
    codigo         VARCHAR(30)   NOT NULL,
    nombre         VARCHAR(150)  NOT NULL,
    descripcion    TEXT,
    ubicacion      VARCHAR(200),
    lider_id       BIGINT,
    fecha_inicio   DATE,
    fecha_fin      DATE,
    estado         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
    observacion    TEXT,
    created_by     BIGINT,
    updated_by     BIGINT,
    deleted_by     BIGINT,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMP,

    CONSTRAINT chk_frente_estado CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'FINALIZADO', 'ANULADO'))
);

CREATE INDEX IF NOT EXISTS idx_frente_empresa  ON proyecto_frente(empresa_id);
CREATE INDEX IF NOT EXISTS idx_frente_proyecto ON proyecto_frente(proyecto_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS proyecto_frente_trabajador (
    id             BIGSERIAL     PRIMARY KEY,
    empresa_id     INT           NOT NULL REFERENCES empresa(id),
    proyecto_id    BIGINT        NOT NULL REFERENCES proyecto(id),
    frente_id      BIGINT        NOT NULL REFERENCES proyecto_frente(id),
    empleado_id    BIGINT        NOT NULL REFERENCES empleados(id),
    fecha_inicio   DATE,
    fecha_fin      DATE,
    estado         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
    observacion    TEXT,
    created_by     BIGINT,
    updated_by     BIGINT,
    deleted_by     BIGINT,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMP,

    CONSTRAINT chk_frente_trab_estado CHECK (estado IN ('ACTIVO', 'RETIRADO', 'SUSPENDIDO', 'ANULADO'))
);

CREATE INDEX IF NOT EXISTS idx_frente_trab_empresa  ON proyecto_frente_trabajador(empresa_id);
CREATE INDEX IF NOT EXISTS idx_frente_trab_frente   ON proyecto_frente_trabajador(frente_id);
CREATE INDEX IF NOT EXISTS idx_frente_trab_empleado ON proyecto_frente_trabajador(empleado_id);
