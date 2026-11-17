-- V233 — Fórmulas, "hace base" y centro de costo por concepto
-- (docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md, F2/F3/F7).
--
-- formula: expresión del lenguaje cerrado de Aura (base = 'FORMULA'). Se analiza
--   y se evalúa con un intérprete propio: nunca se ejecuta como código.
-- hace_base_prestacional: el concepto suma a la base de prima y cesantías
--   (salario + auxilio + lo marcado). FALSE por defecto = la base no cambia.
-- centro_costo_id: el costo del concepto va entero a ese centro, sin repartirse
--   por los centros de costo de la vinculación.
--
-- Solo agrega columnas y amplía el CHECK de la base.

ALTER TABLE concepto_nomina ADD COLUMN IF NOT EXISTS formula TEXT;
ALTER TABLE concepto_nomina ADD COLUMN IF NOT EXISTS hace_base_prestacional BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE concepto_nomina ADD COLUMN IF NOT EXISTS centro_costo_id BIGINT REFERENCES centros_costos(id);

ALTER TABLE concepto_nomina DROP CONSTRAINT IF EXISTS chk_cn_base;
ALTER TABLE concepto_nomina ADD CONSTRAINT chk_cn_base CHECK (base IN ('SALARIO', 'SALARIO_MAS_AUXILIO', 'IBC',
    'DEVENGADO_TOTAL', 'FIJO', 'MANUAL', 'NOVEDAD', 'FORMULA'));

ALTER TABLE concepto_nomina DROP CONSTRAINT IF EXISTS chk_cn_formula;
ALTER TABLE concepto_nomina ADD CONSTRAINT chk_cn_formula CHECK (base <> 'FORMULA' OR formula IS NOT NULL);
