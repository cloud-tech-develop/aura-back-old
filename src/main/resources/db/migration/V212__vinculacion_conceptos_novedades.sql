-- V212 — Vinculación: acto administrativo, municipio de trabajo, conceptos y
-- novedades permitidas (docs/PLAN_NOMINA_REDISENO.md §3; la ficha del empleado
-- se reemplaza por la vinculación).
--
-- vinculacion_concepto: conceptos de nómina propios de la vinculación (p. ej.
--   bonificación fija, auxilio de rodamiento) con su valor y vigencia.
-- vinculacion_novedad_permitida: qué conceptos se le pueden registrar como
--   novedad y con qué tope por período.
-- El concepto se referencia por código: concepto_nomina guarda versiones por
-- vigencia con el mismo código.

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS doc_interno_tipo VARCHAR(30);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS municipio_trabajo_id BIGINT;

CREATE TABLE IF NOT EXISTS vinculacion_concepto (
    id               BIGSERIAL     PRIMARY KEY,
    contrato_id      BIGINT        NOT NULL REFERENCES contrato_laboral(id) ON DELETE CASCADE,
    concepto_codigo  VARCHAR(30)   NOT NULL,
    cantidad         NUMERIC(10,2),
    valor            NUMERIC(18,2),
    porcentaje       NUMERIC(7,4),
    fecha_desde      DATE          NOT NULL,
    fecha_hasta      DATE,
    observacion      VARCHAR(300),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP,
    deleted_at       TIMESTAMP,
    CONSTRAINT ck_vinc_concepto_fechas CHECK (fecha_hasta IS NULL OR fecha_hasta >= fecha_desde)
);

CREATE INDEX IF NOT EXISTS idx_vinc_concepto_contrato ON vinculacion_concepto (contrato_id);

CREATE TABLE IF NOT EXISTS vinculacion_novedad_permitida (
    id                     BIGSERIAL     PRIMARY KEY,
    contrato_id            BIGINT        NOT NULL REFERENCES contrato_laboral(id) ON DELETE CASCADE,
    concepto_codigo        VARCHAR(30)   NOT NULL,
    tope_cantidad          NUMERIC(10,2),
    tope_valor             NUMERIC(18,2),
    requiere_autorizacion  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_vinc_novedad_concepto
    ON vinculacion_novedad_permitida (contrato_id, concepto_codigo);
