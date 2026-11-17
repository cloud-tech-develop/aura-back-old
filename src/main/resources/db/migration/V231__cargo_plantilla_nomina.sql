-- V231 — Cargo como plantilla de nómina (docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md, F5).
--
-- El cargo define los conceptos que se liquidan siempre, las novedades que se le
-- pueden reportar (con sus topes) y el máximo de horas extra y recargos. La
-- vinculación los heredará en F6. Los conceptos se referencian por su NÚMERO
-- visible (concepto_nomina.numero), que comparten sus versiones de vigencia.
--
-- Solo agrega tablas y columnas.

ALTER TABLE nomina_cargo ADD COLUMN IF NOT EXISTS perfil TEXT;
ALTER TABLE nomina_cargo ADD COLUMN IF NOT EXISTS desempeno TEXT;

CREATE TABLE IF NOT EXISTS nomina_cargo_concepto (
    id               BIGSERIAL     PRIMARY KEY,
    cargo_id         BIGINT        NOT NULL REFERENCES nomina_cargo(id) ON DELETE CASCADE,
    concepto_numero  INT           NOT NULL,
    cantidad         NUMERIC(10,2),
    valor            NUMERIC(18,2),
    porcentaje       NUMERIC(7,4),
    observacion      VARCHAR(300),
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cargo_concepto ON nomina_cargo_concepto (cargo_id, concepto_numero);

CREATE TABLE IF NOT EXISTS nomina_cargo_novedad (
    id                     BIGSERIAL     PRIMARY KEY,
    cargo_id               BIGINT        NOT NULL REFERENCES nomina_cargo(id) ON DELETE CASCADE,
    concepto_numero        INT           NOT NULL,
    tope_cantidad          NUMERIC(10,2),
    tope_valor             NUMERIC(18,2),
    requiere_autorizacion  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cargo_novedad ON nomina_cargo_novedad (cargo_id, concepto_numero);

-- Topes por tipo de hora: tipos 3–9 de nomina_concepto_tipo (horas extra y recargos).
CREATE TABLE IF NOT EXISTS nomina_cargo_tope_horas (
    id                BIGSERIAL     PRIMARY KEY,
    cargo_id          BIGINT        NOT NULL REFERENCES nomina_cargo(id) ON DELETE CASCADE,
    tipo_concepto_id  INT           NOT NULL REFERENCES nomina_concepto_tipo(id),
    tope_dia          NUMERIC(5,2),
    tope_semana       NUMERIC(5,2),
    tope_mes          NUMERIC(6,2),
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,
    CONSTRAINT ck_cargo_tope_horas_tipo CHECK (tipo_concepto_id BETWEEN 3 AND 9)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cargo_tope_horas ON nomina_cargo_tope_horas (cargo_id, tipo_concepto_id);

CREATE TABLE IF NOT EXISTS nomina_cargo_adjunto (
    id              BIGSERIAL     PRIMARY KEY,
    empresa_id      INT           NOT NULL REFERENCES empresa(id),
    cargo_id        BIGINT        NOT NULL REFERENCES nomina_cargo(id) ON DELETE CASCADE,
    nombre_archivo  VARCHAR(255)  NOT NULL,
    archivo_url     VARCHAR(600)  NOT NULL,
    content_type    VARCHAR(120),
    tamano_bytes    BIGINT,
    usuario_id      INT,
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cargo_adjunto_cargo ON nomina_cargo_adjunto (cargo_id);
