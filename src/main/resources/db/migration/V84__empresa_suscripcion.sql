-- ── V84: Membresías / suscripciones de clientes (platform) ──────────────────
-- Cada empresa (tenant) es un cliente. Puede ser de pago ÚNICO (activa indefinida)
-- o MENSUAL (con fecha_proximo_pago). El estado VENCIDA se calcula (no se persiste);
-- el impago solo alerta, no suspende automáticamente. Historial en suscripcion_pago.

CREATE TABLE IF NOT EXISTS empresa_suscripcion (
    id                    BIGSERIAL     PRIMARY KEY,
    empresa_id            INT           NOT NULL REFERENCES empresa(id),
    tipo_plan             VARCHAR(20)   NOT NULL DEFAULT 'MENSUAL',
    estado                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVA',
    valor                 NUMERIC(15,2) NOT NULL DEFAULT 0,
    moneda                VARCHAR(3)    NOT NULL DEFAULT 'COP',
    fecha_inicio          DATE,
    fecha_proximo_pago    DATE,
    dia_cobro             INT,
    contacto_nombre       VARCHAR(150),
    contacto_email        VARCHAR(150),
    contacto_telefono     VARCHAR(40),
    notas                 TEXT,
    created_by            BIGINT,
    updated_by            BIGINT,
    deleted_by            BIGINT,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP,

    CONSTRAINT chk_suscripcion_tipo   CHECK (tipo_plan IN ('UNICO', 'MENSUAL')),
    CONSTRAINT chk_suscripcion_estado CHECK (estado IN ('PRUEBA', 'ACTIVA', 'SUSPENDIDA', 'CANCELADA'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_suscripcion_empresa
    ON empresa_suscripcion(empresa_id) WHERE deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS suscripcion_pago (
    id                    BIGSERIAL     PRIMARY KEY,
    empresa_id            INT           NOT NULL REFERENCES empresa(id),
    suscripcion_id        BIGINT        NOT NULL REFERENCES empresa_suscripcion(id) ON DELETE CASCADE,
    fecha_pago            DATE          NOT NULL,
    monto                 NUMERIC(15,2) NOT NULL DEFAULT 0,
    metodo                VARCHAR(20),
    periodo_desde         DATE,
    periodo_hasta         DATE,
    referencia            VARCHAR(100),
    observacion           VARCHAR(255),
    created_by            BIGINT,
    updated_by            BIGINT,
    deleted_by            BIGINT,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP,

    CONSTRAINT chk_suscripcion_pago_metodo CHECK (metodo IN ('EFECTIVO', 'TRANSFERENCIA', 'TARJETA', 'PASARELA', 'OTRO'))
);

CREATE INDEX IF NOT EXISTS idx_suscripcion_pago_susc    ON suscripcion_pago(suscripcion_id);
CREATE INDEX IF NOT EXISTS idx_suscripcion_pago_empresa ON suscripcion_pago(empresa_id, fecha_pago);
