-- V213 — Hoja de vida del empleado (docs/PLAN_TERCERO_PERSONA_EMPLEADO.md T3, T5, T6).
--
-- empleado_cuenta_bancaria: varias cuentas, una principal. La principal se copia
--   al tercero (banco_tercero_id, banco, tipo_cuenta, numero_cuenta), que es de
--   donde leen el pago de nómina y la nómina electrónica.
-- empleado_familiar: núcleo familiar y beneficiarios (caja, salud).
-- empleado_experiencia: historial laboral (dónde trabajó antes).

CREATE TABLE IF NOT EXISTS empleado_cuenta_bancaria (
    id                BIGSERIAL     PRIMARY KEY,
    empleado_id       BIGINT        NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    banco_tercero_id  BIGINT        NOT NULL REFERENCES tercero(id),
    tipo_cuenta       VARCHAR(25)   NOT NULL,
    numero_cuenta     VARCHAR(50)   NOT NULL,
    principal         BOOLEAN       NOT NULL DEFAULT FALSE,
    activa            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT ck_emp_cuenta_tipo CHECK (tipo_cuenta IN ('AHORROS', 'CORRIENTE', 'DEPOSITO_ELECTRONICO'))
);

CREATE INDEX IF NOT EXISTS idx_emp_cuenta_empleado ON empleado_cuenta_bancaria (empleado_id);

CREATE TABLE IF NOT EXISTS empleado_familiar (
    id                       BIGSERIAL     PRIMARY KEY,
    empleado_id              BIGINT        NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    parentesco               VARCHAR(20)   NOT NULL,
    tipo_documento           VARCHAR(10),
    numero_documento         VARCHAR(30),
    nombre                   VARCHAR(150)  NOT NULL,
    fecha_nacimiento         DATE,
    genero                   VARCHAR(10),
    telefono                 VARCHAR(30),
    ocupacion                VARCHAR(100),
    convive                  BOOLEAN       NOT NULL DEFAULT FALSE,
    depende_economicamente   BOOLEAN       NOT NULL DEFAULT FALSE,
    beneficiario_caja        BOOLEAN       NOT NULL DEFAULT FALSE,
    beneficiario_salud       BOOLEAN       NOT NULL DEFAULT FALSE,
    contacto_emergencia      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP,
    CONSTRAINT ck_emp_familiar_parentesco CHECK (parentesco IN
        ('CONYUGE', 'COMPANERO', 'HIJO', 'HIJASTRO', 'PADRE', 'MADRE', 'HERMANO', 'ABUELO', 'NIETO', 'OTRO'))
);

CREATE INDEX IF NOT EXISTS idx_emp_familiar_empleado ON empleado_familiar (empleado_id);

CREATE TABLE IF NOT EXISTS empleado_experiencia (
    id              BIGSERIAL     PRIMARY KEY,
    empleado_id     BIGINT        NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    empresa         VARCHAR(150)  NOT NULL,
    cargo           VARCHAR(150)  NOT NULL,
    fecha_inicio    DATE          NOT NULL,
    fecha_fin       DATE,
    motivo_retiro   VARCHAR(150),
    jefe_inmediato  VARCHAR(150),
    telefono        VARCHAR(30),
    funciones       VARCHAR(1000),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT ck_emp_experiencia_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);

CREATE INDEX IF NOT EXISTS idx_emp_experiencia_empleado ON empleado_experiencia (empleado_id);

-- La cuenta que ya tenía cada empleado en su tercero pasa a ser su cuenta principal.
INSERT INTO empleado_cuenta_bancaria (empleado_id, banco_tercero_id, tipo_cuenta, numero_cuenta, principal)
SELECT e.id, t.banco_tercero_id,
       CASE WHEN UPPER(t.tipo_cuenta) = 'CORRIENTE' THEN 'CORRIENTE' ELSE 'AHORROS' END,
       TRIM(t.numero_cuenta), TRUE
  FROM empleados e
  JOIN tercero t ON t.id = e.tercero_id
 WHERE t.banco_tercero_id IS NOT NULL AND t.numero_cuenta IS NOT NULL AND TRIM(t.numero_cuenta) <> ''
   AND NOT EXISTS (SELECT 1 FROM empleado_cuenta_bancaria c WHERE c.empleado_id = e.id);
