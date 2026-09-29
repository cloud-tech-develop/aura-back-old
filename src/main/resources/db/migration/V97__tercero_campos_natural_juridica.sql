-- ── V97: Fase 1.a — completar `tercero` para persona natural y jurídica ─────
--
-- Cierra las brechas que bloquean nómina electrónica (DIAN) y PILA (UGPP).
-- Ver Fase 1.a del PLAN_MIGRACION_NOMINA.md.
--
-- ⚠️ Esta migración solo AGREGA columnas. El backfill de nombre1/2 + apellido1/2
--    desde `nombres`/`apellidos` NO va aquí: requiere revisión humana (partir
--    "JUAN CARLOS DE LA ROSA GOMEZ" por heurística falla). Ver V99 y el script
--    de reconciliación.

ALTER TABLE tercero
    -- ── Identificación desagregada ──────────────────────────────────────────
    -- DIAN (nómina electrónica) y UGPP (registro tipo 02 de PILA) exigen los
    -- cuatro componentes POR SEPARADO. No es cosmético.
    ADD COLUMN IF NOT EXISTS nombre1   VARCHAR(40),
    ADD COLUMN IF NOT EXISTS nombre2   VARCHAR(40),
    ADD COLUMN IF NOT EXISTS apellido1 VARCHAR(40),
    ADD COLUMN IF NOT EXISTS apellido2 VARCHAR(40),

    -- ── Persona natural ─────────────────────────────────────────────────────
    ADD COLUMN IF NOT EXISTS fecha_nacimiento           DATE,       -- PILA lo exige
    ADD COLUMN IF NOT EXISTS sexo                       VARCHAR(10),-- PILA lo exige
    ADD COLUMN IF NOT EXISTS fecha_expedicion_documento DATE,
    ADD COLUMN IF NOT EXISTS municipio_expedicion_id    BIGINT,

    -- ── Persona jurídica ────────────────────────────────────────────────────
    -- Representante legal: obligatorio en el encabezado de PILA (aportante).
    ADD COLUMN IF NOT EXISTS nombre_comercial              VARCHAR(150),
    ADD COLUMN IF NOT EXISTS representante_legal_nombre    VARCHAR(150),
    ADD COLUMN IF NOT EXISTS representante_legal_documento VARCHAR(30),

    -- ── Fiscal ──────────────────────────────────────────────────────────────
    -- `auto_retenedor` (V52) es un solo boolean, pero son autorretenciones
    -- distintas: se puede ser de renta y no de ICA. Afecta RetencionesSugeridasDto.
    ADD COLUMN IF NOT EXISTS es_autoretenedor_ica    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS es_autoretenedor_fuente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS declarante              BOOLEAN NOT NULL DEFAULT FALSE,

    -- ── Bancario ────────────────────────────────────────────────────────────
    -- Hoy vive en `empleados`. Al mover la identidad a `tercero` (V99) queda
    -- huérfano allá. Se cablea a FK en V101.
    ADD COLUMN IF NOT EXISTS banco         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tipo_cuenta   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS numero_cuenta VARCHAR(50);

-- Semilla del boolean legacy: quien era autorretenedor lo era de fuente.
-- Revisar con contabilidad si algún tercero es autorretenedor de ICA.
UPDATE tercero
   SET es_autoretenedor_fuente = TRUE
 WHERE auto_retenedor = TRUE;

COMMENT ON COLUMN tercero.nombre1   IS 'Primer nombre. DIAN/UGPP exigen desagregado.';
COMMENT ON COLUMN tercero.apellido1 IS 'Primer apellido. DIAN/UGPP exigen desagregado.';
COMMENT ON COLUMN tercero.sexo      IS 'M | F | OTRO. Requerido por PILA.';

-- ── Contactos múltiples ─────────────────────────────────────────────────────
-- Hoy `tercero` tiene un solo `telefono`/`email` escalar. Un proveedor con
-- contacto de compras, de cartera y de despachos es normal.
--
-- Tabla, NO columna JSON: el ERP de referencia tiene ambos a la vez y nadie
-- sabe cuál está vivo. Ese es el antipatrón a evitar.
CREATE TABLE IF NOT EXISTS tercero_contacto (
    id                    BIGSERIAL    PRIMARY KEY,
    tercero_id            BIGINT       NOT NULL REFERENCES tercero(id) ON DELETE CASCADE,
    nombre                VARCHAR(150) NOT NULL,
    cargo                 VARCHAR(100),
    email                 VARCHAR(150),
    telefono_fijo         VARCHAR(40),
    telefono_celular      VARCHAR(40),
    direccion_facturacion VARCHAR(200),
    direccion_envio       VARCHAR(200),
    notas                 VARCHAR(500),
    activo                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tercero_contacto_tercero ON tercero_contacto(tercero_id);
