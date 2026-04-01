-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  V44 — Plan de Cuentas (PUC Colombia) + Asientos Contables      ║
-- ╚══════════════════════════════════════════════════════════════════╝

-- ── Plan de Cuentas ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS plan_cuenta (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  INTEGER       NOT NULL REFERENCES empresa(id),
    codigo      VARCHAR(20)   NOT NULL,
    nombre      VARCHAR(200)  NOT NULL,
    tipo        VARCHAR(20)   NOT NULL,  -- ACTIVO | PASIVO | PATRIMONIO | INGRESO | GASTO | COSTO | ORDEN
    naturaleza  VARCHAR(10)   NOT NULL,  -- DEBITO | CREDITO
    nivel       SMALLINT      NOT NULL DEFAULT 1,
    padre_id    BIGINT        REFERENCES plan_cuenta(id),
    activa      BOOLEAN       NOT NULL DEFAULT TRUE,
    auxiliar    BOOLEAN       NOT NULL DEFAULT FALSE, -- puede recibir movimientos directos
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_plan_cuenta_empresa_codigo ON plan_cuenta(empresa_id, codigo);
CREATE INDEX idx_plan_cuenta_empresa ON plan_cuenta(empresa_id);
CREATE INDEX idx_plan_cuenta_padre   ON plan_cuenta(padre_id);

-- ── Asientos Contables ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS asiento_contable (
    id              BIGSERIAL PRIMARY KEY,
    empresa_id      INTEGER       NOT NULL REFERENCES empresa(id),
    fecha           DATE          NOT NULL,
    descripcion     VARCHAR(500)  NOT NULL,
    tipo_origen     VARCHAR(30)   NOT NULL DEFAULT 'MANUAL', -- MANUAL | VENTA | COMPRA | GASTO | NOMINA | TESORERIA
    origen_id       BIGINT,       -- FK al id de la transacción origen
    total_debito    NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_credito   NUMERIC(18,2) NOT NULL DEFAULT 0,
    estado          VARCHAR(20)   NOT NULL DEFAULT 'CONTABILIZADO', -- BORRADOR | CONTABILIZADO | ANULADO
    usuario_id      INTEGER,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_asiento_empresa_fecha ON asiento_contable(empresa_id, fecha);
CREATE INDEX idx_asiento_origen        ON asiento_contable(tipo_origen, origen_id);

-- ── Detalles de Asiento ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS asiento_detalle (
    id              BIGSERIAL PRIMARY KEY,
    asiento_id      BIGINT        NOT NULL REFERENCES asiento_contable(id) ON DELETE CASCADE,
    cuenta_id       BIGINT        NOT NULL REFERENCES plan_cuenta(id),
    descripcion     VARCHAR(300),
    debito          NUMERIC(18,2) NOT NULL DEFAULT 0,
    credito         NUMERIC(18,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_asiento_detalle_asiento ON asiento_detalle(asiento_id);
CREATE INDEX idx_asiento_detalle_cuenta  ON asiento_detalle(cuenta_id);
