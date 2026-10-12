-- V210 — Nómina R2: vinculación ampliada (docs/PLAN_NOMINA_REDISENO.md §3).
--
-- contrato_laboral pasa a ser la "vinculación" completa: tipo de vinculación,
-- cargo y grupo del catálogo, remuneración, jornada, período de prueba,
-- ubicación, jefe directo y soportes. Las columnas viejas se conservan y se
-- mantienen sincronizadas (cargo = nombre del cargo, es_salario_integral =
-- remuneración INTEGRAL, periodicidad = frecuencia de pago) porque el motor,
-- PILA y la nómina electrónica las leen.
--
-- Migra los contratos actuales: código consecutivo, tipo de vinculación por el
-- tipo de contrato y cargos del catálogo creados desde el texto libre.

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS codigo VARCHAR(20);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS tipo_vinculacion_id BIGINT REFERENCES nomina_tipo_vinculacion(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS cargo_id BIGINT REFERENCES nomina_cargo(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS grupo_nomina_id BIGINT REFERENCES nomina_catalogo(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS calendario_id BIGINT REFERENCES nomina_catalogo(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS riesgo_arl_id BIGINT REFERENCES nomina_catalogo(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS tipo_remuneracion VARCHAR(20) NOT NULL DEFAULT 'MENSUAL';

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS horas_semana NUMERIC(5,2);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS temporal BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS fecha_fin_prueba DATE;

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS primer_empleo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS sucursal_id INTEGER REFERENCES sucursal(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS area VARCHAR(100);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS municipio_trabajo VARCHAR(120);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS direccion_trabajo VARCHAR(200);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS telefono_trabajo VARCHAR(30);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS jefe_directo_id BIGINT REFERENCES empleados(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS evaluador_id BIGINT REFERENCES empleados(id);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS en_extranjero BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS doc_interno_numero VARCHAR(50);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS doc_interno_fecha DATE;

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS doc_interno_url VARCHAR(500);

ALTER TABLE contrato_laboral ADD COLUMN IF NOT EXISTS doc_externo_url VARCHAR(500);

ALTER TABLE contrato_laboral DROP CONSTRAINT IF EXISTS ck_contrato_tipo_remuneracion;

ALTER TABLE contrato_laboral ADD CONSTRAINT ck_contrato_tipo_remuneracion
    CHECK (tipo_remuneracion IN ('MENSUAL', 'INTEGRAL', 'JORNAL', 'HORA'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_contrato_laboral_codigo
    ON contrato_laboral (empresa_id, codigo) WHERE deleted_at IS NULL AND codigo IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contrato_laboral_cargo ON contrato_laboral (cargo_id);

CREATE INDEX IF NOT EXISTS idx_contrato_laboral_tipo_vinc ON contrato_laboral (tipo_vinculacion_id);

-- ── Migración de los contratos existentes ──────────────────────────────────

-- Remuneración: los integrales ya estaban marcados.
UPDATE contrato_laboral SET tipo_remuneracion = 'INTEGRAL'
 WHERE es_salario_integral = TRUE AND tipo_remuneracion = 'MENSUAL';

-- Código consecutivo por empresa (VIN-00001…) en orden de creación.
UPDATE contrato_laboral c SET codigo = x.codigo
  FROM (SELECT id, 'VIN-' || LPAD(ROW_NUMBER() OVER (PARTITION BY empresa_id ORDER BY id)::TEXT, 5, '0') AS codigo
          FROM contrato_laboral WHERE codigo IS NULL AND deleted_at IS NULL) x
 WHERE c.id = x.id;

-- Tipos de vinculación iniciales en las empresas que ya tienen contratos (la
-- misma semilla que siembra el módulo al abrirse).
INSERT INTO nomina_tipo_vinculacion (empresa_id, codigo, nombre, tipo_contrato, tipo_remuneracion,
       frecuencia_pago, exige_fecha_fin, unica_por_empleado, activo, created_at)
SELECT e.empresa_id, s.codigo, s.nombre, s.contrato, s.remuneracion, s.frecuencia, s.exige_fin, TRUE, TRUE, now()
  FROM (SELECT DISTINCT empresa_id FROM contrato_laboral WHERE deleted_at IS NULL) e
 CROSS JOIN (VALUES
       ('PLANTA',   'Planta (término indefinido)', 'INDEFINIDO',  'MENSUAL',  'MENSUAL',   FALSE),
       ('FIJO',     'Término fijo',                'FIJO',        'MENSUAL',  'MENSUAL',   TRUE),
       ('OBRA',     'Obra o labor',                'OBRA_LABOR',  'MENSUAL',  'MENSUAL',   TRUE),
       ('INTEGRAL', 'Salario integral',            'INDEFINIDO',  'INTEGRAL', 'MENSUAL',   FALSE),
       ('APRENDIZ', 'Aprendiz SENA',               'APRENDIZAJE', 'MENSUAL',  'MENSUAL',   TRUE),
       ('JORNAL',   'Por días (jornal)',           'OBRA_LABOR',  'JORNAL',   'QUINCENAL', TRUE)
     ) AS s(codigo, nombre, contrato, remuneracion, frecuencia, exige_fin)
 WHERE NOT EXISTS (SELECT 1 FROM nomina_tipo_vinculacion t
                    WHERE t.empresa_id = e.empresa_id AND t.codigo = s.codigo AND t.deleted_at IS NULL);

-- Tipo de vinculación según el tipo de contrato (prestación de servicios no es
-- vínculo laboral: queda sin tipo para que el usuario decida).
UPDATE contrato_laboral c SET tipo_vinculacion_id = t.id
  FROM nomina_tipo_vinculacion t
 WHERE c.tipo_vinculacion_id IS NULL AND c.deleted_at IS NULL
   AND t.empresa_id = c.empresa_id AND t.deleted_at IS NULL
   AND t.codigo = CASE
         WHEN c.tipo_remuneracion = 'INTEGRAL' THEN 'INTEGRAL'
         WHEN c.tipo_contrato = 'INDEFINIDO' THEN 'PLANTA'
         WHEN c.tipo_contrato = 'FIJO' THEN 'FIJO'
         WHEN c.tipo_contrato = 'OBRA_LABOR' THEN 'OBRA'
         WHEN c.tipo_contrato = 'APRENDIZAJE' THEN 'APRENDIZ'
       END;

-- Cargos del catálogo a partir del texto libre (uno por nombre distinto, código
-- MIG-001…; el usuario puede recodificarlos después).
INSERT INTO nomina_cargo (empresa_id, codigo, nombre, activo, created_at)
SELECT x.empresa_id,
       'MIG-' || LPAD(x.n::TEXT, 3, '0'),
       x.nombre, TRUE, now()
  FROM (SELECT empresa_id, nombre, ROW_NUMBER() OVER (PARTITION BY empresa_id ORDER BY nombre) AS n
          FROM (SELECT DISTINCT c.empresa_id, TRIM(c.cargo) AS nombre
                  FROM contrato_laboral c
                 WHERE c.deleted_at IS NULL AND c.cargo IS NOT NULL AND TRIM(c.cargo) <> ''
                   AND NOT EXISTS (SELECT 1 FROM nomina_cargo k
                                    WHERE k.empresa_id = c.empresa_id AND k.deleted_at IS NULL
                                      AND LOWER(k.nombre) = LOWER(TRIM(c.cargo)))) d) x
 WHERE NOT EXISTS (SELECT 1 FROM nomina_cargo k
                    WHERE k.empresa_id = x.empresa_id AND k.deleted_at IS NULL
                      AND k.codigo = 'MIG-' || LPAD(x.n::TEXT, 3, '0'));

UPDATE contrato_laboral c SET cargo_id = k.id
  FROM nomina_cargo k
 WHERE c.cargo_id IS NULL AND c.deleted_at IS NULL
   AND k.empresa_id = c.empresa_id AND k.deleted_at IS NULL
   AND LOWER(k.nombre) = LOWER(TRIM(c.cargo));
