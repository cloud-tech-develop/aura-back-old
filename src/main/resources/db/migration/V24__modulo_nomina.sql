-- ── V24: Módulo de Nómina ─────────────────────────────────────────────────────

-- Empleados
CREATE TABLE empleados (
    id                  BIGSERIAL    PRIMARY KEY,
    empresa_id          INT          NOT NULL REFERENCES empresa(id),
    nombres             VARCHAR(100) NOT NULL,
    apellidos           VARCHAR(100) NOT NULL,
    tipo_documento      VARCHAR(20)  NOT NULL DEFAULT 'CC',
                        -- CC | CE | PASAPORTE | NIT
    numero_documento    VARCHAR(30)  NOT NULL,
    cargo               VARCHAR(100),
    fecha_ingreso       DATE         NOT NULL,
    fecha_retiro        DATE,
    salario_base        NUMERIC(15,2) NOT NULL,
    tipo_contrato       VARCHAR(30)  NOT NULL DEFAULT 'INDEFINIDO',
                        -- INDEFINIDO | FIJO | OBRA_LABOR | PRESTACION_SERVICIOS
    banco               VARCHAR(100),
    numero_cuenta       VARCHAR(50),
    tipo_cuenta         VARCHAR(20),
                        -- AHORROS | CORRIENTE
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_empleado_tipo_doc
        CHECK (tipo_documento IN ('CC', 'CE', 'PASAPORTE', 'NIT')),
    CONSTRAINT chk_empleado_contrato
        CHECK (tipo_contrato IN ('INDEFINIDO', 'FIJO', 'OBRA_LABOR', 'PRESTACION_SERVICIOS'))
);

CREATE INDEX idx_empleados_empresa ON empleados(empresa_id);
CREATE INDEX idx_empleados_doc     ON empleados(numero_documento, empresa_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Configuración de nómina por empresa
CREATE TABLE nomina_config (
    id                      BIGSERIAL    PRIMARY KEY,
    empresa_id              INT          NOT NULL UNIQUE REFERENCES empresa(id),
    modo_nomina             VARCHAR(20)  NOT NULL DEFAULT 'SIMPLIFICADO',
                            -- COMPLETO | SIMPLIFICADO
    periodicidad            VARCHAR(20)  NOT NULL DEFAULT 'MENSUAL',
                            -- MENSUAL | QUINCENAL | SEMANAL
    smmlv                   NUMERIC(15,2) NOT NULL DEFAULT 1423500,
    auxilio_transporte      NUMERIC(15,2) NOT NULL DEFAULT 200000,
    pct_salud_empleado      NUMERIC(5,2) NOT NULL DEFAULT 4.00,
    pct_pension_empleado    NUMERIC(5,2) NOT NULL DEFAULT 4.00,
    pct_salud_empleador     NUMERIC(5,2) NOT NULL DEFAULT 8.50,
    pct_pension_empleador   NUMERIC(5,2) NOT NULL DEFAULT 12.00,
    pct_caja_compensacion   NUMERIC(5,2) NOT NULL DEFAULT 4.00,
    pct_icbf                NUMERIC(5,2) NOT NULL DEFAULT 3.00,
    pct_sena                NUMERIC(5,2) NOT NULL DEFAULT 2.00,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_nomina_config_modo
        CHECK (modo_nomina IN ('COMPLETO', 'SIMPLIFICADO')),
    CONSTRAINT chk_nomina_config_periodicidad
        CHECK (periodicidad IN ('MENSUAL', 'QUINCENAL', 'SEMANAL'))
);

-- ─────────────────────────────────────────────────────────────────────────────

-- Nivel de riesgo ARL por empleado
CREATE TABLE empleado_arl (
    id              BIGSERIAL    PRIMARY KEY,
    empresa_id      INT          NOT NULL REFERENCES empresa(id),
    empleado_id     BIGINT       NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    nivel_riesgo    INT          NOT NULL DEFAULT 1,
                    -- 1=0.522% | 2=1.044% | 3=2.436% | 4=4.350% | 5=6.960%
    porcentaje      NUMERIC(5,3) NOT NULL DEFAULT 0.522,

    CONSTRAINT chk_arl_nivel CHECK (nivel_riesgo BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX idx_empleado_arl_unico ON empleado_arl(empleado_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Período de nómina
CREATE TABLE periodo_nomina (
    id              BIGSERIAL    PRIMARY KEY,
    empresa_id      INT          NOT NULL REFERENCES empresa(id),
    fecha_inicio    DATE         NOT NULL,
    fecha_fin       DATE         NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'ABIERTO',
                    -- ABIERTO | LIQUIDADO | PAGADO | ANULADO
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_periodo_estado
        CHECK (estado IN ('ABIERTO', 'LIQUIDADO', 'PAGADO', 'ANULADO'))
);

CREATE INDEX idx_periodo_nomina_empresa ON periodo_nomina(empresa_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Nómina (cabecera por empleado por período)
CREATE TABLE nomina (
    id                      BIGSERIAL    PRIMARY KEY,
    empresa_id              INT          NOT NULL REFERENCES empresa(id),
    periodo_id              BIGINT       NOT NULL REFERENCES periodo_nomina(id),
    empleado_id             BIGINT       NOT NULL REFERENCES empleados(id),
    salario_base            NUMERIC(15,2) NOT NULL,
    dias_trabajados         INT          NOT NULL DEFAULT 30,
    -- Devengados
    salario_proporcional    NUMERIC(15,2) NOT NULL DEFAULT 0,
    auxilio_transporte      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_novedades_dev     NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_devengado         NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Deducciones empleado
    deduccion_salud         NUMERIC(15,2) NOT NULL DEFAULT 0,
    deduccion_pension       NUMERIC(15,2) NOT NULL DEFAULT 0,
    deduccion_otros         NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_deducciones       NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Neto a pagar
    neto_pagar              NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Aportes empleador (solo modo COMPLETO)
    aporte_salud            NUMERIC(15,2) NOT NULL DEFAULT 0,
    aporte_pension          NUMERIC(15,2) NOT NULL DEFAULT 0,
    aporte_arl              NUMERIC(15,2) NOT NULL DEFAULT 0,
    aporte_caja             NUMERIC(15,2) NOT NULL DEFAULT 0,
    aporte_icbf             NUMERIC(15,2) NOT NULL DEFAULT 0,
    aporte_sena             NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Provisiones mensuales (solo modo COMPLETO)
    provision_prima         NUMERIC(15,2) NOT NULL DEFAULT 0,
    provision_cesantias     NUMERIC(15,2) NOT NULL DEFAULT 0,
    provision_int_cesantias NUMERIC(15,2) NOT NULL DEFAULT 0,
    provision_vacaciones    NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Estado
    estado                  VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
                            -- BORRADOR | APROBADO | PAGADO | ANULADO
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_nomina_estado
        CHECK (estado IN ('BORRADOR', 'APROBADO', 'PAGADO', 'ANULADO')),
    CONSTRAINT uq_nomina_empleado_periodo
        UNIQUE (empleado_id, periodo_id)
);

CREATE INDEX idx_nomina_empresa  ON nomina(empresa_id);
CREATE INDEX idx_nomina_periodo  ON nomina(periodo_id);
CREATE INDEX idx_nomina_empleado ON nomina(empleado_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Novedades de nómina (horas extras, bonos, incapacidades, préstamos, etc.)
CREATE TABLE nomina_novedad (
    id              BIGSERIAL    PRIMARY KEY,
    nomina_id       BIGINT       NOT NULL REFERENCES nomina(id) ON DELETE CASCADE,
    tipo            VARCHAR(40)  NOT NULL,
                    -- HORA_EXTRA_DIURNA | HORA_EXTRA_NOCTURNA | HORA_EXTRA_DOMINICAL
                    -- HORA_EXTRA_FESTIVO | INCAPACIDAD | LICENCIA_REMUNERADA
                    -- BONO | COMISION | PRESTAMO | EMBARGO | OTRO_DEVENGO | OTRO_DESCUENTO
    descripcion     VARCHAR(200),
    cantidad        NUMERIC(10,2) NOT NULL DEFAULT 1,
    valor_unitario  NUMERIC(15,2) NOT NULL,
    valor_total     NUMERIC(15,2) NOT NULL,
    es_deduccion    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_novedad_tipo CHECK (
        tipo IN (
            'HORA_EXTRA_DIURNA', 'HORA_EXTRA_NOCTURNA', 'HORA_EXTRA_DOMINICAL',
            'HORA_EXTRA_FESTIVO', 'INCAPACIDAD', 'LICENCIA_REMUNERADA',
            'BONO', 'COMISION', 'PRESTAMO', 'EMBARGO', 'OTRO_DEVENGO', 'OTRO_DESCUENTO'
        )
    )
);

CREATE INDEX idx_nomina_novedad_nomina ON nomina_novedad(nomina_id);
