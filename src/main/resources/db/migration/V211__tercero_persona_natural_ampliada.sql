-- V211 — Tercero persona natural ampliado (docs/PLAN_TERCERO_PERSONA_EMPLEADO.md, T1).
--
-- Identidad completa de la persona natural (sea cliente, proveedor o empleado):
-- RUT, país de expedición, estado civil, RH, lugar de nacimiento, zona, comuna y
-- barrio de residencia, contacto, fotos y firma. Teléfonos y correos pasan a ser
-- varios (tablas hijas); tercero.telefono y tercero.email se siguen llenando con
-- el primero para lo que ya los lee (facturas, PDF, envíos).

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS rut VARCHAR(20);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS codigo_pais_expedicion VARCHAR(3);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(20);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS rh VARCHAR(3);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS codigo_pais_nacimiento VARCHAR(3);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS municipio_nacimiento_id BIGINT;

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS zona VARCHAR(10);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS comuna VARCHAR(80);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS barrio VARCHAR(80);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS contacto_nombre VARCHAR(150);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS foto_oficial_url VARCHAR(500);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS foto_perfil_url VARCHAR(500);

ALTER TABLE tercero ADD COLUMN IF NOT EXISTS firma_url VARCHAR(500);

CREATE TABLE IF NOT EXISTS tercero_telefono (
    id           BIGSERIAL    PRIMARY KEY,
    tercero_id   BIGINT       NOT NULL REFERENCES tercero(id) ON DELETE CASCADE,
    de_contacto  BOOLEAN      NOT NULL DEFAULT FALSE,
    tipo         VARCHAR(15)  NOT NULL DEFAULT 'CELULAR',
    numero       VARCHAR(30)  NOT NULL,
    extension    VARCHAR(10),
    orden        INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_tercero_telefono_tipo CHECK (tipo IN ('CELULAR', 'CASA', 'OFICINA', 'OTRO'))
);

CREATE INDEX IF NOT EXISTS idx_tercero_telefono_tercero ON tercero_telefono (tercero_id);

CREATE TABLE IF NOT EXISTS tercero_correo (
    id           BIGSERIAL    PRIMARY KEY,
    tercero_id   BIGINT       NOT NULL REFERENCES tercero(id) ON DELETE CASCADE,
    de_contacto  BOOLEAN      NOT NULL DEFAULT FALSE,
    correo       VARCHAR(150) NOT NULL,
    orden        INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tercero_correo_tercero ON tercero_correo (tercero_id);

-- Los teléfonos y correos que ya existen pasan a la lista (sin duplicar si se corre otra vez).
INSERT INTO tercero_telefono (tercero_id, tipo, numero, orden)
SELECT t.id, 'CELULAR', TRIM(t.telefono), 0
  FROM tercero t
 WHERE t.telefono IS NOT NULL AND TRIM(t.telefono) <> ''
   AND NOT EXISTS (SELECT 1 FROM tercero_telefono x WHERE x.tercero_id = t.id);

INSERT INTO tercero_correo (tercero_id, correo, orden)
SELECT t.id, TRIM(t.email), 0
  FROM tercero t
 WHERE t.email IS NOT NULL AND TRIM(t.email) <> ''
   AND NOT EXISTS (SELECT 1 FROM tercero_correo x WHERE x.tercero_id = t.id);
