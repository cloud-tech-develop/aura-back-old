-- V217 — Conceptos de novedad (rediseño de nómina R3.2d).
--
-- Hasta aquí las novedades que PAGAN (horas extra, incapacidad, licencias, bono,
-- comisión…) entraban a las bases (IBC, aportes) pero el motor no emitía su línea
-- de devengado: no llegaban al devengado ni al neto. Ahora cada tipo de novedad
-- tiene su concepto (código = tipo de la novedad) y el motor emite una línea por
-- novedad. Así también casan los topes de "novedades permitidas" de la
-- vinculación, que se guardan por código de concepto.
--
-- base = 'NOVEDAD': el valor lo trae la novedad (o el concepto propio de la
-- vinculación); el motor no lo calcula en su recorrido de conceptos.
-- VACACIONES ya existía y se reutiliza.

ALTER TABLE concepto_nomina DROP CONSTRAINT IF EXISTS chk_cn_base;

ALTER TABLE concepto_nomina ADD CONSTRAINT chk_cn_base CHECK (base IN ('SALARIO', 'SALARIO_MAS_AUXILIO', 'IBC',
    'DEVENGADO_TOTAL', 'FIJO', 'MANUAL', 'NOVEDAD'));

INSERT INTO concepto_nomina (empresa_id, codigo, nombre, clase, constituye_ibc, base, vigente_desde, codigo_dian,
       orden, activo, created_at)
SELECT NULL, v.codigo, v.nombre, 'DEVENGADO', v.ibc, 'NOVEDAD', DATE '2020-01-01', v.dian, v.orden, TRUE, now()
  FROM (VALUES
       ('HORA_EXTRA_DIURNA',    'Hora extra diurna',             TRUE,  'HED',            30),
       ('HORA_EXTRA_NOCTURNA',  'Hora extra nocturna',           TRUE,  'HEN',            31),
       ('HORA_EXTRA_DOMINICAL', 'Hora extra dominical',          TRUE,  'HEDDF',          32),
       ('HORA_EXTRA_FESTIVO',   'Hora extra festiva',            TRUE,  'HEDDF',          33),
       ('INCAPACIDAD',          'Incapacidad',                   TRUE,  'Incapacidades',  40),
       ('LICENCIA_REMUNERADA',  'Licencia remunerada',           TRUE,  'LicenciaR',      41),
       ('LICENCIA_MATERNIDAD',  'Licencia de maternidad/paternidad', TRUE, 'LicenciaMP',  42),
       ('BONO',                 'Bonificación',                  FALSE, 'Bonificaciones', 50),
       ('COMISION',             'Comisión',                      TRUE,  'Comisiones',     51),
       ('OTRO_DEVENGO',         'Otro devengo',                  TRUE,  'OtrosConceptos', 52)
       ) AS v(codigo, nombre, ibc, dian, orden)
 WHERE NOT EXISTS (SELECT 1 FROM concepto_nomina c WHERE c.empresa_id IS NULL AND c.codigo = v.codigo);
