-- E8 · Cierre anual fiscal (C9): provisión de renta, traslado de utilidad al
-- abrir el año y distribución de utilidades post-asamblea (reserva legal,
-- dividendos y su pago). El sistema SUGIERE valores; el contador DIGITA.

-- ── Operaciones del cierre de ejercicio (provisión renta / traslado) ──────
CREATE TABLE IF NOT EXISTS cierre_anual (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  INT           NOT NULL,
    anio        INT           NOT NULL,
    tipo        VARCHAR(20)   NOT NULL,            -- PROVISION_RENTA | TRASLADO
    monto       NUMERIC(18,2) NOT NULL,            -- TRASLADO: >0 utilidad, <0 pérdida
    detalle     VARCHAR(300),
    fecha       DATE          NOT NULL,
    usuario_id  BIGINT,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, anio, tipo)
);

-- ── Distribución de utilidades (post-asamblea) ────────────────────────────
CREATE TABLE IF NOT EXISTS distribucion_utilidades (
    id            BIGSERIAL PRIMARY KEY,
    empresa_id    INT           NOT NULL,
    anio          INT           NOT NULL,
    utilidad_base NUMERIC(18,2) NOT NULL,           -- saldo 3705 al momento de distribuir
    reserva_legal NUMERIC(18,2) NOT NULL DEFAULT 0,
    dividendos    NUMERIC(18,2) NOT NULL DEFAULT 0,
    observaciones VARCHAR(300),
    fecha         DATE          NOT NULL,
    usuario_id    BIGINT,
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, anio)
);

CREATE TABLE IF NOT EXISTS dividendo_pago (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         INT           NOT NULL,
    distribucion_id    BIGINT        NOT NULL REFERENCES distribucion_utilidades(id),
    monto              NUMERIC(18,2) NOT NULL,
    metodo_pago        VARCHAR(40),
    cuenta_bancaria_id BIGINT,
    tercero_id         BIGINT,
    fecha              DATE          NOT NULL,
    usuario_id         BIGINT,
    created_at         TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_dividendo_pago_distribucion
    ON dividendo_pago (distribucion_id);

-- ── Cuentas nuevas para empresas EXISTENTES (idempotente) ────────────────
-- Grupos nivel 2 que faltan: 54 (impuesto de renta) y 33 (reservas).
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT c.empresa_id, '54', 'Impuesto de Renta y Complementarios', 'GASTO', 'DEBITO', 2, c.id, TRUE, FALSE, now()
FROM plan_cuenta c WHERE c.codigo = '5'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = c.empresa_id AND x.codigo = '54');

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT c.empresa_id, '33', 'Reservas', 'PATRIMONIO', 'CREDITO', 2, c.id, TRUE, FALSE, now()
FROM plan_cuenta c WHERE c.codigo = '3'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = c.empresa_id AND x.codigo = '33');

-- Cuentas de movimiento nivel 3.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT p.empresa_id, v.codigo, v.nombre, v.tipo, v.naturaleza, 3, p.id, TRUE, TRUE, now()
FROM (VALUES ('5405', 'Impuesto de Renta y Complementarios', 'GASTO',      'DEBITO',  '54'),
             ('2404', 'Impuesto de Renta por Pagar',         'PASIVO',     'CREDITO', '24'),
             ('2360', 'Dividendos o Participaciones por Pagar','PASIVO',   'CREDITO', '23'),
             ('3305', 'Reservas Obligatorias',               'PATRIMONIO', 'CREDITO', '33')
     ) AS v(codigo, nombre, tipo, naturaleza, padre)
JOIN plan_cuenta p ON p.codigo = v.padre
WHERE NOT EXISTS (SELECT 1 FROM plan_cuenta x
                  WHERE x.empresa_id = p.empresa_id AND x.codigo = v.codigo);

-- Auxiliar nivel 4: la reserva legal cuelga de 3305.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT p.empresa_id, '330505', 'Reserva Legal', 'PATRIMONIO', 'CREDITO', 4, p.id, TRUE, TRUE, now()
FROM plan_cuenta p WHERE p.codigo = '3305'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = p.empresa_id AND x.codigo = '330505');
