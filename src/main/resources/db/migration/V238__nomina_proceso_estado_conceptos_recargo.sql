-- V238 — Dos arreglos para liquidar nóminas con horas de asistencia o frentes.
--
-- 1) proceso_nomina.estado medía 20 y el estado COMPLETADO_CON_ERRORES tiene 22.
--    Cuando un solo empleado del lote fallaba, el proceso no podía cerrarse
--    ("value too long for type character varying(20)") y se perdía la causa
--    real del empleado. Se amplía a 30 (ampliar un VARCHAR no reescribe la tabla).
--
-- 2) La asistencia y los frentes generan novedades RECARGO_NOCTURNO,
--    RECARGO_DOMINICAL_FESTIVO y HORA_EXTRA_DOMINICAL_FESTIVA, pero no había
--    concepto con ese código: el motor las pagaba como "Otro devengo" y nunca
--    podían ponerse en las novedades permitidas del cargo ("no está permitida en
--    su vinculación ni en su cargo"). Se crean como conceptos de ley globales con
--    las mismas banderas (IBC, base prestacional) que la hora extra diurna.
--
-- Idempotente.

ALTER TABLE proceso_nomina ALTER COLUMN estado TYPE VARCHAR(30);

INSERT INTO concepto_nomina (empresa_id, codigo, nombre, clase, constituye_ibc, base, vigente_desde, codigo_dian,
       orden, activo, created_at, numero, tipo_id, de_ley, hace_base_prestacional)
SELECT NULL, v.codigo, v.nombre, 'DEVENGADO',
       COALESCE((SELECT h.constituye_ibc FROM concepto_nomina h
                  WHERE h.empresa_id IS NULL AND h.codigo = 'HORA_EXTRA_DIURNA'
                  ORDER BY h.vigente_desde DESC LIMIT 1), TRUE),
       'NOVEDAD', DATE '2020-01-01', v.dian, v.orden, TRUE, now(), v.numero, v.tipo_id, TRUE,
       COALESCE((SELECT h.hace_base_prestacional FROM concepto_nomina h
                  WHERE h.empresa_id IS NULL AND h.codigo = 'HORA_EXTRA_DIURNA'
                  ORDER BY h.vigente_desde DESC LIMIT 1), FALSE)
  FROM (VALUES
       ('RECARGO_NOCTURNO',             'Recargo nocturno',                  'HRN',   34, 14, 7),
       ('RECARGO_DOMINICAL_FESTIVO',    'Recargo dominical/festivo',         'HRDDF', 35, 15, 8),
       ('HORA_EXTRA_DOMINICAL_FESTIVA', 'Hora extra dominical/festiva',      'HEDDF', 36, 16, 5)
       ) AS v(codigo, nombre, dian, orden, numero, tipo_id)
 WHERE NOT EXISTS (SELECT 1 FROM concepto_nomina c WHERE c.empresa_id IS NULL AND c.codigo = v.codigo)
   AND NOT EXISTS (SELECT 1 FROM concepto_nomina c WHERE c.empresa_id IS NULL AND c.numero = v.numero);
