-- E5 · Impuestos como reglas parametrizables (C3).
-- Cada impuesto define su % y sus cuentas (generado en ventas, descontable
-- en compras). Corrige la deuda del IVA: 240801 generado / 240802
-- descontable, que hoy se mezclan en 2408 y rompen el reporte de IVA neto.

CREATE TABLE IF NOT EXISTS impuesto (
    id                    BIGSERIAL PRIMARY KEY,
    empresa_id            INT          NOT NULL,
    nombre                VARCHAR(80)  NOT NULL,
    tipo                  VARCHAR(20)  NOT NULL,        -- IVA | INC | EXCLUIDO | EXENTO
    porcentaje            NUMERIC(6,3) NOT NULL DEFAULT 0,
    cuenta_generado_id    BIGINT,                        -- ventas (240801)
    cuenta_descontable_id BIGINT,                        -- compras (240802)
    vigente_desde         DATE,
    vigente_hasta         DATE,
    activo                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, nombre)
);

CREATE INDEX IF NOT EXISTS idx_impuesto_empresa ON impuesto (empresa_id, activo);

ALTER TABLE producto
    ADD COLUMN IF NOT EXISTS impuesto_id BIGINT;

-- La tarifa de retención puede traer su propia cuenta (fallback: conceptos
-- 2365/2367/2368).
ALTER TABLE tarifa_retencion
    ADD COLUMN IF NOT EXISTS cuenta_contable_id BIGINT;

-- ── Migración de datos: empresas existentes ──────────────────────────────

-- 1. Subcuentas 240801/240802 bajo la 2408 de cada empresa que la tenga.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel,
                         padre_id, activa, auxiliar, created_at)
SELECT pc.empresa_id, '240801', 'IVA Generado', 'PASIVO', 'CREDITO', 4,
       pc.id, TRUE, TRUE, now()
FROM plan_cuenta pc
WHERE pc.codigo = '2408'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x
                  WHERE x.empresa_id = pc.empresa_id AND x.codigo = '240801');

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel,
                         padre_id, activa, auxiliar, created_at)
SELECT pc.empresa_id, '240802', 'IVA Descontable', 'PASIVO', 'CREDITO', 4,
       pc.id, TRUE, TRUE, now()
FROM plan_cuenta pc
WHERE pc.codigo = '2408'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x
                  WHERE x.empresa_id = pc.empresa_id AND x.codigo = '240802');

-- 2. La configuración concepto→cuenta apunta a las subcuentas nuevas.
--    (La historia NO se toca: los asientos viejos conservan su cuenta 2408.)
UPDATE cuenta_config cc
SET cuenta_id = pc.id
FROM plan_cuenta pc
WHERE cc.concepto = 'IVA_GENERADO'
  AND pc.empresa_id = cc.empresa_id AND pc.codigo = '240801';

UPDATE cuenta_config cc
SET cuenta_id = pc.id
FROM plan_cuenta pc
WHERE cc.concepto = 'IVA_DESCONTABLE'
  AND pc.empresa_id = cc.empresa_id AND pc.codigo = '240802';

-- 3. Seed de impuestos estándar por empresa con PUC.
INSERT INTO impuesto (empresa_id, nombre, tipo, porcentaje,
                      cuenta_generado_id, cuenta_descontable_id, activo)
SELECT e.empresa_id, s.nombre, s.tipo, s.porcentaje,
       g.id, d.id, TRUE
FROM (SELECT DISTINCT empresa_id FROM plan_cuenta) e
CROSS JOIN (VALUES ('IVA 19%', 'IVA', 19.0),
                   ('IVA 5%',  'IVA', 5.0),
                   ('INC 8%',  'INC', 8.0),
                   ('Excluido', 'EXCLUIDO', 0.0),
                   ('Exento',   'EXENTO', 0.0)) AS s(nombre, tipo, porcentaje)
LEFT JOIN plan_cuenta g ON g.empresa_id = e.empresa_id AND g.codigo = '240801'
LEFT JOIN plan_cuenta d ON d.empresa_id = e.empresa_id AND d.codigo = '240802'
WHERE NOT EXISTS (SELECT 1 FROM impuesto i
                  WHERE i.empresa_id = e.empresa_id AND i.nombre = s.nombre);

-- 4. Mapear el % IVA actual de cada producto a su impuesto equivalente.
UPDATE producto p
SET impuesto_id = i.id
FROM impuesto i
WHERE p.impuesto_id IS NULL
  AND i.empresa_id = p.empresa_id
  AND ((p.iva_porcentaje = 19 AND i.nombre = 'IVA 19%')
    OR (p.iva_porcentaje = 5  AND i.nombre = 'IVA 5%')
    OR (p.iva_porcentaje = 8  AND i.nombre = 'INC 8%')
    OR ((p.iva_porcentaje IS NULL OR p.iva_porcentaje = 0) AND i.nombre = 'Excluido'));
