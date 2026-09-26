-- V174 — Notas contables: clasificación, reversión, soportes y plantillas.
--
-- N3 Reversión: la nota contabilizada no se corrige editando ni anulando un
--    mes cerrado; se registra la nota inversa en un mes abierto. La original
--    SIGUE CONTABILIZADA (los informes suman las dos y netean en cero): no se
--    marca REVERTIDO porque los reportes solo leen CONTABILIZADO y la reversión
--    quedaría restando sola. Los enlaces van en las dos direcciones.
-- N4 Clasificación (ajuste, provisión…) para filtrar y para el revisor fiscal,
--    y soportes adjuntos (factura del servicio, cálculo de la provisión).
-- N5 Plantillas: notas que se repiten (amortizaciones, provisiones fijas); si
--    son recurrentes, el sistema deja un BORRADOR cada mes para aprobar.

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS clasificacion VARCHAR(30);

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS reversa_de_id BIGINT;

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS revertido_por_id BIGINT;

-- Al contabilizar, generar también la reversión el día 1 del mes siguiente.
ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS reversion_automatica BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS plantilla_id BIGINT;

-- Una nota tiene a lo sumo una reversión viva (anulada la reversión, se puede
-- volver a reversar).
CREATE UNIQUE INDEX IF NOT EXISTS ux_asiento_reversa_de
    ON asiento_contable (reversa_de_id)
    WHERE reversa_de_id IS NOT NULL AND estado <> 'ANULADO';

-- ── Soportes ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nota_diario_soporte (
    id              BIGSERIAL     PRIMARY KEY,
    empresa_id      INTEGER       NOT NULL,
    asiento_id      BIGINT        NOT NULL REFERENCES asiento_contable(id) ON DELETE CASCADE,
    nombre_archivo  VARCHAR(255)  NOT NULL,
    archivo_url     VARCHAR(1000) NOT NULL,
    content_type    VARCHAR(100),
    tamano_bytes    BIGINT,
    usuario_id      INTEGER,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_nota_diario_soporte_asiento
    ON nota_diario_soporte (asiento_id) WHERE deleted_at IS NULL;

-- ── Plantillas ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nota_diario_plantilla (
    id              BIGSERIAL     PRIMARY KEY,
    empresa_id      INTEGER       NOT NULL,
    nombre          VARCHAR(150)  NOT NULL,
    descripcion     VARCHAR(500)  NOT NULL,
    clasificacion   VARCHAR(30),
    recurrente      BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Día del mes en que se genera el borrador (1–28: todos los meses lo tienen).
    dia_mes         SMALLINT,
    -- Último mes generado, 'YYYY-MM': impide generar dos veces el mismo mes.
    ultimo_periodo  VARCHAR(7),
    activa          BOOLEAN       NOT NULL DEFAULT TRUE,
    usuario_id      INTEGER,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_nota_diario_plantilla_empresa
    ON nota_diario_plantilla (empresa_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS nota_diario_plantilla_linea (
    id               BIGSERIAL     PRIMARY KEY,
    plantilla_id     BIGINT        NOT NULL REFERENCES nota_diario_plantilla(id) ON DELETE CASCADE,
    orden            INTEGER       NOT NULL DEFAULT 0,
    cuenta_id        BIGINT        NOT NULL,
    descripcion      VARCHAR(300),
    debito           NUMERIC(18,2) NOT NULL DEFAULT 0,
    credito          NUMERIC(18,2) NOT NULL DEFAULT 0,
    tercero_id       BIGINT,
    centro_costo_id  BIGINT,
    proyecto_id      BIGINT,
    frente_id        BIGINT
);

CREATE INDEX IF NOT EXISTS ix_nota_diario_plantilla_linea_plantilla
    ON nota_diario_plantilla_linea (plantilla_id);
