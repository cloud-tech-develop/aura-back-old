-- V236 — Nube de documentos por empresa (docs/PLAN_NUBE_DOCUMENTOS.md, F1).
--
-- Cada empresa tiene su espacio: carpetas y archivos de máximo 1 MB. Los suben
-- sus usuarios y la firma contable que la atiende mientras el vínculo esté vigente.
-- Los bytes viven cifrados en el bucket privado aura-nube; aquí solo la clave del
-- objeto (nunca una URL), el IV y la versión de la llave.
--
-- Aislamiento reforzado en la base: un archivo o subcarpeta solo puede colgar de
-- una carpeta de SU MISMA empresa (llave foránea compuesta id + empresa_id).
-- Así, aunque el backend tuviera un error, la base no deja mezclar empresas.
--
-- nube_evento es la bitácora: solo INSERT; un trigger impide editarla o borrarla.
--
-- Idempotente.

-- ── Espacio de la empresa: cuota y aceptación de términos ───────────────
CREATE TABLE IF NOT EXISTS nube_espacio (
    empresa_id          INT          PRIMARY KEY REFERENCES empresa(id),
    cuota_mb            INT          NOT NULL DEFAULT 500 CHECK (cuota_mb > 0),
    max_archivos        INT          NOT NULL DEFAULT 5000 CHECK (max_archivos > 0),
    terminos_version    VARCHAR(10),
    terminos_aceptados_por INT,
    terminos_aceptados_at  TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP
);

-- ── Carpetas ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nube_carpeta (
    id                  BIGSERIAL    PRIMARY KEY,
    empresa_id          INT          NOT NULL REFERENCES empresa(id),
    padre_id            BIGINT,
    nombre              VARCHAR(150) NOT NULL,
    -- COMPARTIDA: la ven la empresa y su firma. SOLO_FIRMA: papeles de trabajo del contador.
    visibilidad         VARCHAR(15)  NOT NULL DEFAULT 'COMPARTIDA'
                        CHECK (visibilidad IN ('COMPARTIDA', 'SOLO_FIRMA')),
    nivel               SMALLINT     NOT NULL DEFAULT 1 CHECK (nivel BETWEEN 1 AND 6),
    creado_por          INT          NOT NULL,
    -- Firma por la que entró quien la creó (null = usuario de la propia empresa).
    creado_por_firma_id INT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    eliminado_por       INT,
    purgar_en           TIMESTAMP,
    CONSTRAINT uq_nube_carpeta_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT ck_nube_carpeta_no_es_su_padre CHECK (padre_id IS NULL OR padre_id <> id)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_nube_carpeta_padre_misma_empresa') THEN
        ALTER TABLE nube_carpeta
            ADD CONSTRAINT fk_nube_carpeta_padre_misma_empresa
            FOREIGN KEY (padre_id, empresa_id) REFERENCES nube_carpeta (id, empresa_id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_nube_carpeta_nombre
    ON nube_carpeta (empresa_id, COALESCE(padre_id, 0), lower(nombre))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_nube_carpeta_padre ON nube_carpeta (empresa_id, padre_id);
CREATE INDEX IF NOT EXISTS idx_nube_carpeta_purga ON nube_carpeta (purgar_en) WHERE purgar_en IS NOT NULL;

-- ── Archivos ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nube_archivo (
    id                  BIGSERIAL    PRIMARY KEY,
    empresa_id          INT          NOT NULL REFERENCES empresa(id),
    carpeta_id          BIGINT,
    nombre              VARCHAR(150) NOT NULL,
    extension           VARCHAR(10)  NOT NULL,
    -- Tipo detectado por el contenido, no el que mandó el navegador.
    mime                VARCHAR(100) NOT NULL,
    tamano_bytes        INT          NOT NULL CHECK (tamano_bytes > 0 AND tamano_bytes <= 1048576),
    -- Huella del archivo original (antes de cifrar): integridad y evidencia.
    sha256              VARCHAR(64)  NOT NULL,
    object_key          VARCHAR(120) NOT NULL,
    iv                  BYTEA        NOT NULL,
    version_llave       SMALLINT     NOT NULL,
    subido_por          INT          NOT NULL,
    subido_por_firma_id INT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    eliminado_por       INT,
    purgar_en           TIMESTAMP,
    CONSTRAINT uq_nube_archivo_object_key UNIQUE (object_key)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_nube_archivo_carpeta_misma_empresa') THEN
        ALTER TABLE nube_archivo
            ADD CONSTRAINT fk_nube_archivo_carpeta_misma_empresa
            FOREIGN KEY (carpeta_id, empresa_id) REFERENCES nube_carpeta (id, empresa_id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_nube_archivo_nombre
    ON nube_archivo (empresa_id, COALESCE(carpeta_id, 0), lower(nombre))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_nube_archivo_carpeta ON nube_archivo (empresa_id, carpeta_id);
CREATE INDEX IF NOT EXISTS idx_nube_archivo_purga ON nube_archivo (purgar_en) WHERE purgar_en IS NOT NULL;

-- ── Bitácora (solo INSERT) ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nube_evento (
    id                  BIGSERIAL    PRIMARY KEY,
    empresa_id          INT          NOT NULL,
    usuario_id          INT          NOT NULL,
    -- Firma por la que entró el usuario (null = usuario de la propia empresa).
    firma_empresa_id    INT,
    accion              VARCHAR(20)  NOT NULL,
    archivo_id          BIGINT,
    carpeta_id          BIGINT,
    detalle             JSONB,
    ip                  VARCHAR(45),
    user_agent          VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_nube_evento_empresa ON nube_evento (empresa_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_nube_evento_archivo ON nube_evento (archivo_id) WHERE archivo_id IS NOT NULL;

CREATE OR REPLACE FUNCTION nube_evento_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'nube_evento es una bitácora: no se puede modificar ni borrar';
END $$;

DROP TRIGGER IF EXISTS trg_nube_evento_inmutable ON nube_evento;
CREATE TRIGGER trg_nube_evento_inmutable
    BEFORE UPDATE OR DELETE ON nube_evento
    FOR EACH ROW EXECUTE FUNCTION nube_evento_inmutable();

-- ── Solicitudes de más espacio (las aprueba la plataforma) ──────────────
CREATE TABLE IF NOT EXISTS nube_solicitud_espacio (
    id                  BIGSERIAL    PRIMARY KEY,
    empresa_id          INT          NOT NULL REFERENCES empresa(id),
    solicitado_por      INT          NOT NULL,
    cuota_actual_mb     INT          NOT NULL,
    cuota_solicitada_mb INT          NOT NULL CHECK (cuota_solicitada_mb > 0),
    estado              VARCHAR(15)  NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    atendido_por        INT,
    atendido_at         TIMESTAMP,
    observacion         VARCHAR(300),
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

-- Una sola solicitud pendiente por empresa.
CREATE UNIQUE INDEX IF NOT EXISTS uq_nube_solicitud_pendiente
    ON nube_solicitud_espacio (empresa_id) WHERE estado = 'PENDIENTE';
