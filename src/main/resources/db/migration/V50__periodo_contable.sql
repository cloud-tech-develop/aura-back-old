-- Tabla de períodos contables (cierre formal por mes/año)
CREATE TABLE periodo_contable (
    id            BIGSERIAL PRIMARY KEY,
    empresa_id    INTEGER      NOT NULL,
    anio          SMALLINT     NOT NULL,
    mes           SMALLINT     NOT NULL,
    estado        VARCHAR(10)  NOT NULL DEFAULT 'ABIERTO'
                  CHECK (estado IN ('ABIERTO', 'CERRADO')),
    fecha_apertura DATE        NOT NULL DEFAULT CURRENT_DATE,
    fecha_cierre   DATE,
    usuario_apertura_id BIGINT,
    usuario_cierre_id   BIGINT,
    observaciones  TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_periodo_empresa_anio_mes UNIQUE (empresa_id, anio, mes)
);

-- Vinculación de asientos al período contable
ALTER TABLE asiento_contable
    ADD COLUMN periodo_contable_id BIGINT
    REFERENCES periodo_contable(id);
