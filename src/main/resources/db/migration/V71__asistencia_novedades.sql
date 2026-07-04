-- ── V71: Asistencia Fase 4 — novedades desde asistencia ──────────────────────

-- Trazabilidad en las novedades de nómina existentes.
ALTER TABLE nomina_novedad
    ADD COLUMN naturaleza          VARCHAR(20)  NOT NULL DEFAULT 'DEVENGADO',
    -- DEVENGADO | DEDUCCION | INFORMATIVO | PROVISION | APORTE_EMPLEADOR
    ADD COLUMN origen              VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    -- MANUAL | ASISTENCIA | IMPORTACION | AJUSTE_ADMIN | RELIQUIDACION | SISTEMA
    ADD COLUMN estado              VARCHAR(20)  NOT NULL DEFAULT 'APLICADA',
    -- PENDIENTE | APROBADA | RECHAZADA | APLICADA | ANULADA
    ADD COLUMN requiere_aprobacion BOOLEAN      NOT NULL DEFAULT FALSE;

-- ─────────────────────────────────────────────────────────────────────────────

-- Novedades generadas desde asistencia (staging por período+empleado, previo a liquidar)
CREATE TABLE asistencia_novedad_nomina (
    id                       BIGSERIAL    PRIMARY KEY,
    empresa_id               INT          NOT NULL REFERENCES empresa(id),
    periodo_nomina_id        BIGINT       REFERENCES periodo_nomina(id),
    asistencia_dia_id        BIGINT       REFERENCES asistencia_dia(id) ON DELETE SET NULL,
    asistencia_incidencia_id BIGINT       REFERENCES asistencia_incidencia(id) ON DELETE SET NULL,
    empleado_id              BIGINT       NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    tipo_novedad             VARCHAR(40)  NOT NULL,
    unidad                   VARCHAR(10)  NOT NULL DEFAULT 'HORAS',
                             -- HORAS | DIAS | MINUTOS | VALOR
    cantidad                 NUMERIC(10,2) NOT NULL DEFAULT 0,
    valor_manual             NUMERIC(15,2),
    origen                   VARCHAR(20)  NOT NULL DEFAULT 'ASISTENCIA',
                             -- ASISTENCIA | AJUSTE_ADMIN | RELIQUIDACION
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
                             -- PENDIENTE | APROBADA | RECHAZADA | ENVIADA_A_NOMINA
    fecha_generacion         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generado_por             INT,

    CONSTRAINT chk_asis_nov_unidad CHECK (unidad IN ('HORAS', 'DIAS', 'MINUTOS', 'VALOR')),
    CONSTRAINT chk_asis_nov_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'ENVIADA_A_NOMINA'))
);

CREATE INDEX idx_asis_nov_empresa  ON asistencia_novedad_nomina(empresa_id);
CREATE INDEX idx_asis_nov_periodo  ON asistencia_novedad_nomina(periodo_nomina_id);
CREATE INDEX idx_asis_nov_empleado ON asistencia_novedad_nomina(empleado_id);
