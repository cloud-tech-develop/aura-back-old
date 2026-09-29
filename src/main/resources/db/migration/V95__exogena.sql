-- E11 · Información exógena DIAN (C7): catálogo de formatos y conceptos,
-- mapeo cuenta/rango PUC → concepto por empresa, lotes versionados con sus
-- líneas (tercero × concepto × valor) y errores del validador previo.

-- ── Catálogo global (no depende de la empresa) ────────────────────────────
CREATE TABLE IF NOT EXISTS exogena_formato (
    id           BIGSERIAL PRIMARY KEY,
    codigo       VARCHAR(10)  NOT NULL UNIQUE,      -- 1001, 1005, 1006, 1007, 1008, 1009, 2276
    nombre       VARCHAR(200) NOT NULL,
    version_dian INT          NOT NULL DEFAULT 1,
    activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS exogena_concepto (
    id         BIGSERIAL PRIMARY KEY,
    formato_id BIGINT       NOT NULL REFERENCES exogena_formato(id),
    codigo     VARCHAR(10)  NOT NULL,               -- 5001, 5002… (o el propio formato)
    nombre     VARCHAR(255) NOT NULL,
    UNIQUE (formato_id, codigo)
);

-- ── Parametrización por empresa: rango PUC → concepto + tipo de valor ─────
-- cuenta_hasta NULL = match por prefijo de cuenta_desde; con valor = rango
-- lexicográfico. Al generar gana el mapeo con el prefijo más específico.
CREATE TABLE IF NOT EXISTS exogena_mapeo_cuenta (
    id           BIGSERIAL PRIMARY KEY,
    empresa_id   INT         NOT NULL,
    concepto_id  BIGINT      NOT NULL REFERENCES exogena_concepto(id),
    cuenta_desde VARCHAR(10) NOT NULL,
    cuenta_hasta VARCHAR(10),
    tipo_valor   VARCHAR(20) NOT NULL,   -- MOVIMIENTO_DB | MOVIMIENTO_CR | SALDO_DB | SALDO_CR
    created_at   TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_exogena_mapeo_empresa ON exogena_mapeo_cuenta (empresa_id);

-- ── Lotes versionados ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS exogena_lote (
    id                   BIGSERIAL PRIMARY KEY,
    empresa_id           INT           NOT NULL,
    formato_id           BIGINT        NOT NULL REFERENCES exogena_formato(id),
    anio                 INT           NOT NULL,
    version              INT           NOT NULL DEFAULT 1,
    estado               VARCHAR(15)   NOT NULL DEFAULT 'BORRADOR',  -- BORRADOR | APROBADO
    cuantia_menor_umbral NUMERIC(18,2) NOT NULL DEFAULT 100000,
    generado_por         BIGINT,
    generado_en          TIMESTAMP     NOT NULL DEFAULT now(),
    aprobado_por         BIGINT,
    aprobado_en          TIMESTAMP,
    UNIQUE (empresa_id, formato_id, anio, version)
);

CREATE TABLE IF NOT EXISTS exogena_linea (
    id            BIGSERIAL PRIMARY KEY,
    lote_id       BIGINT        NOT NULL REFERENCES exogena_lote(id),
    concepto_id   BIGINT        NOT NULL REFERENCES exogena_concepto(id),
    tercero_id    BIGINT,                            -- NULL = cuantías menores (222222222)
    valor         NUMERIC(18,2) NOT NULL,
    cuantia_menor BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_exogena_linea_lote ON exogena_linea (lote_id);

CREATE TABLE IF NOT EXISTS exogena_error (
    id         BIGSERIAL PRIMARY KEY,
    lote_id    BIGINT REFERENCES exogena_lote(id),
    empresa_id INT          NOT NULL,
    anio       INT          NOT NULL,
    tipo       VARCHAR(40)  NOT NULL,   -- TERCERO_INCOMPLETO | SIN_MAPEO | COMPROBANTE_BORRADOR | PERIODO_ABIERTO | SIN_TERCERO
    detalle    VARCHAR(300),
    tercero_id BIGINT,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_exogena_error_lote ON exogena_error (lote_id);

-- ── Seed del catálogo (idempotente) ───────────────────────────────────────
INSERT INTO exogena_formato (codigo, nombre, version_dian)
SELECT v.codigo, v.nombre, v.version_dian
FROM (VALUES ('1001', 'Pagos y abonos en cuenta y retenciones practicadas', 10),
             ('1005', 'Impuesto sobre las ventas descontable',               7),
             ('1006', 'Impuesto sobre las ventas generado',                  7),
             ('1007', 'Ingresos recibidos',                                  9),
             ('1008', 'Saldo de cuentas por cobrar al 31 de diciembre',      7),
             ('1009', 'Saldo de cuentas por pagar al 31 de diciembre',       7),
             ('2276', 'Ingresos y retenciones por rentas de trabajo',        4)
     ) AS v(codigo, nombre, version_dian)
WHERE NOT EXISTS (SELECT 1 FROM exogena_formato f WHERE f.codigo = v.codigo);

INSERT INTO exogena_concepto (formato_id, codigo, nombre)
SELECT f.id, v.concepto, v.nombre
FROM (VALUES ('1001', '5001', 'Salarios y demás pagos laborales'),
             ('1001', '5002', 'Honorarios'),
             ('1001', '5003', 'Comisiones'),
             ('1001', '5004', 'Servicios'),
             ('1001', '5005', 'Arrendamientos'),
             ('1001', '5006', 'Intereses y rendimientos financieros'),
             ('1001', '5007', 'Compra de activos movibles'),
             ('1001', '5008', 'Compra de activos fijos'),
             ('1001', '5016', 'Los demás costos y deducciones'),
             ('1005', '1005', 'IVA descontable'),
             ('1006', '1006', 'IVA generado'),
             ('1007', '4001', 'Ingresos brutos de actividades ordinarias'),
             ('1007', '4002', 'Otros ingresos brutos'),
             ('1008', '1315', 'Cuentas por cobrar a clientes y otros'),
             ('1009', '2201', 'Saldo de pasivos con proveedores y otros'),
             ('2276', '2276', 'Pagos por rentas de trabajo')
     ) AS v(formato, concepto, nombre)
JOIN exogena_formato f ON f.codigo = v.formato
WHERE NOT EXISTS (SELECT 1 FROM exogena_concepto c
                  WHERE c.formato_id = f.id AND c.codigo = v.concepto);

-- ── Mapeos default para empresas EXISTENTES (prefijos del PUC seed) ───────
-- cuenta_hasta NULL = prefijo. El más específico gana (5105 le gana a 51).
INSERT INTO exogena_mapeo_cuenta (empresa_id, concepto_id, cuenta_desde, tipo_valor)
SELECT e.id, c.id, v.desde, v.tipo_valor
FROM (VALUES ('1001', '5001', '5105',   'MOVIMIENTO_DB'),
             ('1001', '5006', '5305',   'MOVIMIENTO_DB'),
             ('1001', '5007', '1435',   'MOVIMIENTO_DB'),
             ('1001', '5008', '15',     'MOVIMIENTO_DB'),
             ('1001', '5016', '51',     'MOVIMIENTO_DB'),
             ('1001', '5016', '52',     'MOVIMIENTO_DB'),
             ('1001', '5016', '53',     'MOVIMIENTO_DB'),
             ('1005', '1005', '240802', 'MOVIMIENTO_DB'),
             ('1006', '1006', '240801', 'MOVIMIENTO_CR'),
             ('1007', '4001', '41',     'MOVIMIENTO_CR'),
             ('1007', '4002', '42',     'MOVIMIENTO_CR'),
             ('1008', '1315', '1305',   'SALDO_DB'),
             ('1009', '2201', '2205',   'SALDO_CR'),
             ('2276', '2276', '5105',   'MOVIMIENTO_DB')
     ) AS v(formato, concepto, desde, tipo_valor)
JOIN exogena_formato f  ON f.codigo = v.formato
JOIN exogena_concepto c ON c.formato_id = f.id AND c.codigo = v.concepto
CROSS JOIN empresa e
WHERE NOT EXISTS (SELECT 1 FROM exogena_mapeo_cuenta m
                  WHERE m.empresa_id = e.id AND m.concepto_id = c.id
                    AND m.cuenta_desde = v.desde);

-- Nota: la depreciación (5160) y el deterioro (5199) quedan bajo el prefijo
-- 51 → 5016; no son pagos a terceros y normalmente van sin tercero, así que
-- el validador los marca SIN_TERCERO y el contador decide excluir el mapeo.
