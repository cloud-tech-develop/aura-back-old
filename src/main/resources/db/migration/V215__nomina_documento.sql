-- V215 — Prenómina y nómina como documentos (rediseño R3.2, docs/PLAN_NOMINA_REDISENO.md §5).
--
-- nomina_documento: encabezado de una prenómina o una nómina, creado con un tipo
--   de comprobante (V214). Número propio por (tipo de comprobante, clase de
--   documento). Cuelga de periodo_nomina, que sigue siendo el rango de fechas que
--   leen el motor, PILA y la nómina electrónica (varios documentos pueden
--   compartir período).
-- nomina.documento_id: fila del empleado dentro del documento. NULL = nómina del
--   flujo viejo por período.
-- nomina.es_prenomina: la fila es de una prenómina. Toda consulta que agregue
--   nóminas (PILA, provisiones, promedios, certificados, dashboard) la excluye:
--   una prenómina nunca es una nómina real.
-- La restricción "un contrato por período" pasa a "un contrato por documento";
-- el flujo viejo (sin documento) la conserva con un índice parcial.

CREATE TABLE IF NOT EXISTS nomina_documento (
    id                   BIGSERIAL      PRIMARY KEY,
    empresa_id           INTEGER        NOT NULL REFERENCES empresa(id),
    periodo_id           BIGINT         NOT NULL REFERENCES periodo_nomina(id),
    tipo_comprobante_id  BIGINT         NOT NULL REFERENCES nomina_tipo_comprobante(id),
    clase_documento      VARCHAR(12)    NOT NULL,
    numero               INTEGER        NOT NULL,
    fecha                DATE           NOT NULL,
    fecha_desde          DATE           NOT NULL,
    fecha_hasta          DATE           NOT NULL,
    fecha_liquidacion    DATE           NOT NULL,
    fecha_pago           DATE,
    simulacion           BOOLEAN        NOT NULL DEFAULT FALSE,
    descripcion          VARCHAR(500),
    estado               VARCHAR(12)    NOT NULL DEFAULT 'PRELIMINAR',
    fecha_confirmacion   TIMESTAMP,
    documento_origen_id  BIGINT         REFERENCES nomina_documento(id),
    total_devengado      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    total_deducido       NUMERIC(18,2)  NOT NULL DEFAULT 0,
    total_neto           NUMERIC(18,2)  NOT NULL DEFAULT 0,
    total_aportes        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    usuario_id           BIGINT,
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP,
    CONSTRAINT ck_nomina_documento_clase CHECK (clase_documento IN ('PRENOMINA', 'NOMINA')),
    CONSTRAINT ck_nomina_documento_estado CHECK (estado IN ('PRELIMINAR', 'CONFIRMADO', 'ANULADO')),
    CONSTRAINT ck_nomina_documento_fechas CHECK (fecha_hasta >= fecha_desde)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_documento_numero
    ON nomina_documento (empresa_id, tipo_comprobante_id, clase_documento, numero);

CREATE INDEX IF NOT EXISTS idx_nomina_documento_empresa_fecha
    ON nomina_documento (empresa_id, fecha_desde DESC);

ALTER TABLE nomina ADD COLUMN IF NOT EXISTS documento_id BIGINT REFERENCES nomina_documento(id);

ALTER TABLE nomina ADD COLUMN IF NOT EXISTS es_prenomina BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE nomina DROP CONSTRAINT IF EXISTS uq_nomina_contrato_periodo;

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_contrato_periodo_sin_documento
    ON nomina (contrato_id, periodo_id) WHERE documento_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_contrato_documento
    ON nomina (contrato_id, documento_id) WHERE documento_id IS NOT NULL;
