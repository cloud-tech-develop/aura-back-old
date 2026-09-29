-- ── V82: Parametrización laboral (G1) ───────────────────────────────────────
-- Jornada legal por vigencia + calendario laboral (festivos) + turno por frente.

-- Config legal de jornada y recargos por vigencia (no quemado en código).
CREATE TABLE IF NOT EXISTS jornada_laboral_config (
    id                        BIGSERIAL     PRIMARY KEY,
    empresa_id                INT           NOT NULL REFERENCES empresa(id),
    fecha_inicio_vigencia     DATE          NOT NULL,
    fecha_fin_vigencia        DATE,
    horas_semanales_legales   NUMERIC(5,2)  NOT NULL DEFAULT 42,
    horas_mensuales_base      NUMERIC(6,2)  NOT NULL DEFAULT 210,
    hora_diurna_inicio        TIME          NOT NULL DEFAULT '06:00',
    hora_diurna_fin           TIME          NOT NULL DEFAULT '19:00',
    hora_nocturna_inicio      TIME          NOT NULL DEFAULT '19:00',
    hora_nocturna_fin         TIME          NOT NULL DEFAULT '06:00',
    recargo_nocturno          NUMERIC(5,2)  NOT NULL DEFAULT 35,
    recargo_extra_diurna      NUMERIC(5,2)  NOT NULL DEFAULT 25,
    recargo_extra_nocturna    NUMERIC(5,2)  NOT NULL DEFAULT 75,
    recargo_dominical_festivo NUMERIC(5,2)  NOT NULL DEFAULT 90,
    max_horas_extra_dia       NUMERIC(5,2)  NOT NULL DEFAULT 2,
    max_horas_extra_semana    NUMERIC(5,2)  NOT NULL DEFAULT 12,
    aplica_excepcion_sectorial BOOLEAN      NOT NULL DEFAULT FALSE,
    sector_excepcion          VARCHAR(80),
    created_by                BIGINT,
    updated_by                BIGINT,
    deleted_by                BIGINT,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jornada_config_empresa_vig
    ON jornada_laboral_config(empresa_id, fecha_inicio_vigencia);

-- ─────────────────────────────────────────────────────────────────────────────

-- Calendario laboral (festivos nacionales/regionales, descansos, cierres).
CREATE TABLE IF NOT EXISTS calendario_laboral (
    id                   BIGSERIAL     PRIMARY KEY,
    empresa_id           INT           NOT NULL REFERENCES empresa(id),
    fecha                DATE          NOT NULL,
    tipo_dia             VARCHAR(30)   NOT NULL,
    nombre               VARCHAR(150),
    aplica_recargo       BOOLEAN       NOT NULL DEFAULT TRUE,
    es_festivo_nacional  BOOLEAN       NOT NULL DEFAULT FALSE,
    es_festivo_regional  BOOLEAN       NOT NULL DEFAULT FALSE,
    es_descanso_empresa  BOOLEAN       NOT NULL DEFAULT FALSE,
    origen               VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    created_by           BIGINT,
    updated_by           BIGINT,
    deleted_by           BIGINT,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at           TIMESTAMP,

    CONSTRAINT chk_calendario_tipo CHECK (tipo_dia IN
        ('LABORAL', 'DOMINGO', 'FESTIVO_NACIONAL', 'FESTIVO_REGIONAL', 'DESCANSO_EMPRESA', 'CIERRE_OPERATIVO', 'COMPENSATORIO'))
);

CREATE INDEX IF NOT EXISTS idx_calendario_empresa_fecha ON calendario_laboral(empresa_id, fecha);
CREATE UNIQUE INDEX IF NOT EXISTS ux_calendario_empresa_fecha
    ON calendario_laboral(empresa_id, fecha) WHERE deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────

-- Turno asignado a nivel de frente (todos los del frente comparten turno).
CREATE TABLE IF NOT EXISTS frente_turno (
    id            BIGSERIAL   PRIMARY KEY,
    empresa_id    INT         NOT NULL REFERENCES empresa(id),
    proyecto_id   BIGINT      NOT NULL REFERENCES proyecto(id),
    frente_id     BIGINT      NOT NULL REFERENCES proyecto_frente(id),
    turno_id      BIGINT      NOT NULL REFERENCES turno_trabajo(id),
    fecha_inicio  DATE,
    fecha_fin     DATE,
    created_by    BIGINT,
    updated_by    BIGINT,
    deleted_by    BIGINT,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_frente_turno_frente ON frente_turno(frente_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Horas ordinarias programadas del turno (base para clasificar ordinaria vs extra).
ALTER TABLE turno_trabajo
    ADD COLUMN IF NOT EXISTS horas_ordinarias_programadas NUMERIC(5,2) NOT NULL DEFAULT 8;
