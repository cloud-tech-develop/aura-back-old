-- V209 — Nómina R1: catálogos de configuración (docs/PLAN_NOMINA_REDISENO.md §9b).
--
-- nomina_catalogo: listas simples de la empresa, una fila por elemento y un
--   "tipo" por catálogo (GRUPO_NOMINA, NIVEL_CARGO, TIPO_CARGO, TIPO_EMBARGO,
--   MOTIVO_RETIRO, MOTIVO_AUSENTISMO, RIESGO_ARL). Lo propio de cada tipo
--   (prioridad y límite de un embargo, quién paga un ausentismo, % de riesgo
--   ARL…) va en "atributos" como JSON en texto; el servicio valida las llaves.
-- nomina_cargo: catálogo de cargos con nivel, tipo, plazas y rango salarial.
-- nomina_tipo_vinculacion: agrupa empleados y fija los valores por defecto de
--   su vinculación (tipo de contrato, remuneración, frecuencia de pago).

CREATE TABLE IF NOT EXISTS nomina_catalogo (
    id           BIGSERIAL     PRIMARY KEY,
    empresa_id   INTEGER       NOT NULL REFERENCES empresa(id),
    tipo         VARCHAR(30)   NOT NULL,
    codigo       VARCHAR(20)   NOT NULL,
    nombre       VARCHAR(150)  NOT NULL,
    descripcion  VARCHAR(500),
    atributos    TEXT,
    orden        INTEGER       NOT NULL DEFAULT 0,
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP,
    deleted_at   TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_catalogo_codigo
    ON nomina_catalogo (empresa_id, tipo, codigo) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_nomina_catalogo_tipo ON nomina_catalogo (empresa_id, tipo);

CREATE TABLE IF NOT EXISTS nomina_cargo (
    id                  BIGSERIAL     PRIMARY KEY,
    empresa_id          INTEGER       NOT NULL REFERENCES empresa(id),
    codigo              VARCHAR(20)   NOT NULL,
    nombre              VARCHAR(150)  NOT NULL,
    nivel_id            BIGINT        REFERENCES nomina_catalogo(id),
    tipo_cargo_id       BIGINT        REFERENCES nomina_catalogo(id),
    plazas              INTEGER,
    salario_referencia  NUMERIC(18,2),
    salario_minimo      NUMERIC(18,2),
    salario_maximo      NUMERIC(18,2),
    mision              TEXT,
    funciones           TEXT,
    activo              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_cargo_codigo
    ON nomina_cargo (empresa_id, codigo) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS nomina_tipo_vinculacion (
    id                  BIGSERIAL     PRIMARY KEY,
    empresa_id          INTEGER       NOT NULL REFERENCES empresa(id),
    codigo              VARCHAR(20)   NOT NULL,
    nombre              VARCHAR(150)  NOT NULL,
    tipo_contrato       VARCHAR(30),
    tipo_remuneracion   VARCHAR(20)   NOT NULL DEFAULT 'MENSUAL',
    frecuencia_pago     VARCHAR(15)   NOT NULL DEFAULT 'MENSUAL',
    exige_fecha_fin     BOOLEAN       NOT NULL DEFAULT FALSE,
    unica_por_empleado  BOOLEAN       NOT NULL DEFAULT TRUE,
    descripcion         VARCHAR(500),
    activo              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    CONSTRAINT ck_nomina_tipo_vinc_remuneracion CHECK (tipo_remuneracion IN ('MENSUAL', 'INTEGRAL', 'JORNAL', 'HORA')),
    CONSTRAINT ck_nomina_tipo_vinc_frecuencia CHECK (frecuencia_pago IN ('MENSUAL', 'QUINCENAL', 'SEMANAL'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_tipo_vinculacion_codigo
    ON nomina_tipo_vinculacion (empresa_id, codigo) WHERE deleted_at IS NULL;
