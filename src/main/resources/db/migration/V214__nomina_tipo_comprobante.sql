-- V214 — Tipos de comprobante de nómina (rediseño R3, docs/PLAN_NOMINA_REDISENO.md §4).
--
-- Cada empresa define sus comprobantes: periódicos ("Nómina administrativa",
-- "Aprendices"…) y no periódicos (prima, cesantías, intereses, liquidación
-- definitiva, retroactivo, otros pagos). La prenómina y la nómina se crean con un
-- tipo de comprobante y toman de él su clase y su numeración (prefijo + consecutivo
-- propio, calculado sobre los documentos del tipo).
--
-- nomina_tipo_comprobante_vinculacion: tipos de vinculación cuyos empleados se
--   pueden agregar al comprobante. Sin filas = todos.
-- La semilla la pone el backend la primera vez que la empresa abre el catálogo.

CREATE TABLE IF NOT EXISTS nomina_tipo_comprobante (
    id           BIGSERIAL     PRIMARY KEY,
    empresa_id   INTEGER       NOT NULL REFERENCES empresa(id),
    codigo       VARCHAR(20)   NOT NULL,
    nombre       VARCHAR(150)  NOT NULL,
    clase        VARCHAR(30)   NOT NULL,
    prefijo      VARCHAR(10)   NOT NULL,
    frecuencia   VARCHAR(15),
    descripcion  VARCHAR(500),
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP,
    deleted_at   TIMESTAMP,
    CONSTRAINT ck_nomina_tipo_comprobante_clase CHECK (clase IN ('PERIODICO', 'PRIMA', 'CESANTIAS',
        'INTERESES_CESANTIAS', 'LIQUIDACION_DEFINITIVA', 'RETROACTIVO', 'OTROS_PAGOS')),
    CONSTRAINT ck_nomina_tipo_comprobante_frecuencia CHECK (frecuencia IS NULL
        OR frecuencia IN ('MENSUAL', 'QUINCENAL', 'SEMANAL'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_tipo_comprobante_codigo
    ON nomina_tipo_comprobante (empresa_id, codigo) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_tipo_comprobante_prefijo
    ON nomina_tipo_comprobante (empresa_id, prefijo) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS nomina_tipo_comprobante_vinculacion (
    id                   BIGSERIAL  PRIMARY KEY,
    tipo_comprobante_id  BIGINT     NOT NULL REFERENCES nomina_tipo_comprobante(id) ON DELETE CASCADE,
    tipo_vinculacion_id  BIGINT     NOT NULL REFERENCES nomina_tipo_vinculacion(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_tipo_comprobante_vinculacion
    ON nomina_tipo_comprobante_vinculacion (tipo_comprobante_id, tipo_vinculacion_id);
