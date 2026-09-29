-- E9 · Conciliación bancaria (C6): extracto importado del banco, matching
-- sugerido contra el libro (movimientos de la cuenta contable del banco) y
-- ajustes desde la misma pantalla (comisiones, GMF 4x1000, intereses).

-- ── Extracto y sus líneas ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS extracto_bancario (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         INT           NOT NULL,
    cuenta_bancaria_id BIGINT        NOT NULL REFERENCES cuenta_bancaria(id),
    periodo            VARCHAR(7)    NOT NULL,           -- '2026-07'
    saldo_inicial      NUMERIC(18,2) NOT NULL DEFAULT 0,
    saldo_final        NUMERIC(18,2) NOT NULL DEFAULT 0,
    estado             VARCHAR(15)   NOT NULL DEFAULT 'ABIERTO',  -- ABIERTO | CONCILIADO
    usuario_id         BIGINT,
    conciliado_at      TIMESTAMP,
    created_at         TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, cuenta_bancaria_id, periodo)
);

CREATE TABLE IF NOT EXISTS extracto_linea (
    id                 BIGSERIAL PRIMARY KEY,
    extracto_id        BIGINT        NOT NULL REFERENCES extracto_bancario(id),
    fecha              DATE          NOT NULL,
    descripcion        VARCHAR(255),
    valor              NUMERIC(18,2) NOT NULL,           -- >0 abono del banco / <0 cargo
    estado             VARCHAR(15)   NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE | CONCILIADO | AJUSTE
    asiento_detalle_id BIGINT,                           -- match confirmado contra el libro
    tipo_ajuste        VARCHAR(20),                      -- GASTO_BANCARIO | GMF | INTERES
    created_at         TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_extracto_linea_extracto ON extracto_linea (extracto_id);
CREATE INDEX IF NOT EXISTS idx_extracto_linea_detalle  ON extracto_linea (asiento_detalle_id);

-- ── Cuentas nuevas para empresas EXISTENTES (idempotente) ────────────────
-- Grupos nivel 2 por si faltan: 53 (gastos no operacionales) y 42 (ingresos
-- no operacionales).
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT c.empresa_id, '53', 'Gastos No Operacionales', 'GASTO', 'DEBITO', 2, c.id, TRUE, FALSE, now()
FROM plan_cuenta c WHERE c.codigo = '5'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = c.empresa_id AND x.codigo = '53');

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT c.empresa_id, '42', 'No Operacionales', 'INGRESO', 'CREDITO', 2, c.id, TRUE, FALSE, now()
FROM plan_cuenta c WHERE c.codigo = '4'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = c.empresa_id AND x.codigo = '42');

-- Cuentas de movimiento nivel 3.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT p.empresa_id, v.codigo, v.nombre, v.tipo, v.naturaleza, 3, p.id, TRUE, TRUE, now()
FROM (VALUES ('5305', 'Financieros',  'GASTO',   'DEBITO',  '53'),
             ('4210', 'Financieros',  'INGRESO', 'CREDITO', '42')
     ) AS v(codigo, nombre, tipo, naturaleza, padre)
JOIN plan_cuenta p ON p.codigo = v.padre
WHERE NOT EXISTS (SELECT 1 FROM plan_cuenta x
                  WHERE x.empresa_id = p.empresa_id AND x.codigo = v.codigo);

-- Auxiliares nivel 4: comisiones y GMF bajo 5305, intereses bajo 4210.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT p.empresa_id, v.codigo, v.nombre, v.tipo, v.naturaleza, 4, p.id, TRUE, TRUE, now()
FROM (VALUES ('530515', 'Comisiones',                             'GASTO',   'DEBITO',  '5305'),
             ('530595', 'Gravamen a los Movimientos Financieros', 'GASTO',   'DEBITO',  '5305'),
             ('421005', 'Intereses',                              'INGRESO', 'CREDITO', '4210')
     ) AS v(codigo, nombre, tipo, naturaleza, padre)
JOIN plan_cuenta p ON p.codigo = v.padre
WHERE NOT EXISTS (SELECT 1 FROM plan_cuenta x
                  WHERE x.empresa_id = p.empresa_id AND x.codigo = v.codigo);
