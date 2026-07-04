-- ── V76: Liquidación de prestaciones sociales (Fase A: prima y vacaciones) ────

CREATE TABLE liquidacion_prestacion (
    id                  BIGSERIAL     PRIMARY KEY,
    empresa_id          INT           NOT NULL REFERENCES empresa(id),
    empleado_id         BIGINT        NOT NULL REFERENCES empleados(id),
    tipo                VARCHAR(30)   NOT NULL,   -- PRIMA | VACACIONES
    fecha_desde         DATE          NOT NULL,
    fecha_hasta         DATE          NOT NULL,
    dias                INT           NOT NULL DEFAULT 0,
    base_salarial       NUMERIC(15,2) NOT NULL DEFAULT 0,
    valor               NUMERIC(15,2) NOT NULL DEFAULT 0,
    estado              VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR',
                        -- BORRADOR | APROBADA | PAGADA | ANULADA
    medio_pago          VARCHAR(20),  -- EFECTIVO | TRANSFERENCIA
    cuenta_bancaria_id  BIGINT        REFERENCES cuenta_bancaria(id),
    fecha_pago          TIMESTAMP,
    observacion         VARCHAR(255),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_prestacion_tipo   CHECK (tipo IN ('PRIMA', 'VACACIONES', 'CESANTIAS', 'INTERESES_CESANTIAS', 'LIQUIDACION_DEFINITIVA', 'INDEMNIZACION')),
    CONSTRAINT chk_prestacion_estado CHECK (estado IN ('BORRADOR', 'APROBADA', 'PAGADA', 'ANULADA'))
);

CREATE INDEX idx_prestacion_empresa  ON liquidacion_prestacion(empresa_id);
CREATE INDEX idx_prestacion_empleado ON liquidacion_prestacion(empleado_id);

