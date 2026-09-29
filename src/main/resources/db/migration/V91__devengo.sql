-- E6 · Ajustes de devengo (C8): anticipos, gastos diferidos, causaciones
-- programadas y deterioro de cartera. NIIF exige base de acumulación: sin
-- esto la contabilidad solo refleja caja y documentos.

-- ── Anticipos (cliente 2805 / proveedor 1330) ────────────────────────────
CREATE TABLE IF NOT EXISTS anticipo (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         INT           NOT NULL,
    tipo               VARCHAR(10)   NOT NULL,            -- CLIENTE | PROVEEDOR
    tercero_id         BIGINT        NOT NULL,
    monto              NUMERIC(18,2) NOT NULL,
    saldo              NUMERIC(18,2) NOT NULL,
    metodo_pago        VARCHAR(40),
    cuenta_bancaria_id BIGINT,
    fecha              DATE          NOT NULL,
    observaciones      VARCHAR(300),
    estado             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO',  -- ACTIVO|APLICADO|ANULADO
    usuario_id         BIGINT,
    created_at         TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_anticipo_empresa_tercero
    ON anticipo (empresa_id, tercero_id, estado);

CREATE TABLE IF NOT EXISTS anticipo_cruce (
    id               BIGSERIAL PRIMARY KEY,
    empresa_id       INT           NOT NULL,
    anticipo_id      BIGINT        NOT NULL REFERENCES anticipo(id),
    cuenta_cobrar_id BIGINT,
    cuenta_pagar_id  BIGINT,
    monto            NUMERIC(18,2) NOT NULL,
    fecha            DATE          NOT NULL,
    usuario_id       BIGINT,
    created_at       TIMESTAMP     NOT NULL DEFAULT now()
);

-- ── Gastos diferidos (1705) ──────────────────────────────────────────────
ALTER TABLE gasto
    ADD COLUMN IF NOT EXISTS es_diferido BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS meses_diferido INT;

CREATE TABLE IF NOT EXISTS diferido_amortizacion (
    id         BIGSERIAL PRIMARY KEY,
    empresa_id INT           NOT NULL,
    gasto_id   BIGINT        NOT NULL,
    periodo    VARCHAR(7)    NOT NULL,                    -- 'yyyy-MM'
    monto      NUMERIC(18,2) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (gasto_id, periodo)
);

-- ── Causaciones programadas (asiento recurrente en BORRADOR) ─────────────
CREATE TABLE IF NOT EXISTS causacion_programada (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  INT          NOT NULL,
    nombre      VARCHAR(120) NOT NULL,
    dia         INT          NOT NULL DEFAULT 1,          -- día del mes que se genera
    activa      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS causacion_programada_linea (
    id           BIGSERIAL PRIMARY KEY,
    causacion_id BIGINT        NOT NULL REFERENCES causacion_programada(id),
    cuenta_id    BIGINT        NOT NULL,
    descripcion  VARCHAR(200),
    debito       NUMERIC(18,2) NOT NULL DEFAULT 0,
    credito      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tercero_id   BIGINT
);
CREATE TABLE IF NOT EXISTS causacion_ejecucion (
    id           BIGSERIAL PRIMARY KEY,
    empresa_id   INT        NOT NULL,
    causacion_id BIGINT     NOT NULL REFERENCES causacion_programada(id),
    periodo      VARCHAR(7) NOT NULL,
    created_at   TIMESTAMP  NOT NULL DEFAULT now(),
    UNIQUE (causacion_id, periodo)
);

-- ── Deterioro de cartera (propuesta que el contador aprueba) ─────────────
CREATE TABLE IF NOT EXISTS deterioro_calculo (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  INT           NOT NULL,
    fecha       DATE          NOT NULL,
    monto       NUMERIC(18,2) NOT NULL,
    detalle     VARCHAR(500),
    usuario_id  BIGINT,
    created_at  TIMESTAMP     NOT NULL DEFAULT now()
);

-- ── Cuentas nuevas para empresas EXISTENTES (idempotente) ────────────────
-- Grupos nivel 2 que faltan: 17 (diferidos) y 28 (anticipos recibidos).
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT c.empresa_id, '17', 'Diferidos', 'ACTIVO', 'DEBITO', 2, c.id, TRUE, FALSE, now()
FROM plan_cuenta c WHERE c.codigo = '1'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = c.empresa_id AND x.codigo = '17');

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT c.empresa_id, '28', 'Otros Pasivos', 'PASIVO', 'CREDITO', 2, c.id, TRUE, FALSE, now()
FROM plan_cuenta c WHERE c.codigo = '2'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = c.empresa_id AND x.codigo = '28');

-- Cuentas de movimiento nivel 3.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT p.empresa_id, v.codigo, v.nombre, v.tipo, v.naturaleza, 3, p.id, TRUE, TRUE, now()
FROM (VALUES ('1330', 'Anticipos a Proveedores',        'ACTIVO', 'DEBITO',  '13'),
             ('1399', 'Provisión Cartera (deterioro)',  'ACTIVO', 'CREDITO', '13'),
             ('1499', 'Provisión Inventarios',          'ACTIVO', 'CREDITO', '14'),
             ('1705', 'Gastos Pagados por Anticipado',  'ACTIVO', 'DEBITO',  '17'),
             ('2805', 'Anticipos de Clientes',          'PASIVO', 'CREDITO', '28'),
             ('5199', 'Provisiones y Deterioros',       'GASTO',  'DEBITO',  '51')
     ) AS v(codigo, nombre, tipo, naturaleza, padre)
JOIN plan_cuenta p ON p.codigo = v.padre
WHERE NOT EXISTS (SELECT 1 FROM plan_cuenta x
                  WHERE x.empresa_id = p.empresa_id AND x.codigo = v.codigo);
