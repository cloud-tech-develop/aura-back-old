-- V199 — Firma contable (docs/PLAN_FIRMA_CONTABLE.md, F1).
--
-- Una firma contable es una empresa más (gratis) que atiende la contabilidad de
-- empresas cliente. Sus usuarios siguen siendo de la firma (usuario.empresa_id no
-- cambia): entran al cliente por el vínculo, con el perfil "Contador externo".
--
-- Regla del día 1: al subir esto nadie gana ni pierde nada. Ninguna empresa nace
-- como firma y no hay vínculos.

-- ── La empresa es una firma contable ────────────────────────────────────────
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS es_firma_contable BOOLEAN NOT NULL DEFAULT FALSE;

-- ── Firma → cliente ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS empresa_firma_cliente (
    id                  BIGSERIAL  PRIMARY KEY,
    firma_empresa_id    INT        NOT NULL REFERENCES empresa(id),
    cliente_empresa_id  INT        NOT NULL REFERENCES empresa(id),
    -- Perfil "Contador externo" del cliente con el que entran los usuarios de la firma.
    perfil_id           BIGINT     REFERENCES perfil(id),
    activo              BOOLEAN    NOT NULL DEFAULT TRUE,
    creado_por          INT,
    created_at          TIMESTAMP  NOT NULL DEFAULT now(),
    -- Quien quitó el acceso: un usuario del cliente o el administrador de plataforma.
    revocado_por        INT,
    revocado_at         TIMESTAMP,
    CONSTRAINT ck_firma_cliente_distintas CHECK (firma_empresa_id <> cliente_empresa_id)
);

-- Un cliente tiene como máximo una firma activa. Quitar y volver a dar el acceso
-- crea una fila nueva: la anterior queda como historia.
CREATE UNIQUE INDEX IF NOT EXISTS uq_firma_cliente_activo
    ON empresa_firma_cliente (cliente_empresa_id) WHERE activo;

CREATE INDEX IF NOT EXISTS idx_firma_cliente_firma
    ON empresa_firma_cliente (firma_empresa_id) WHERE activo;

-- ── Qué usuario de la firma atiende qué cliente ─────────────────────────────
CREATE TABLE IF NOT EXISTS usuario_firma_cliente (
    id          BIGSERIAL  PRIMARY KEY,
    usuario_id  INT        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    vinculo_id  BIGINT     NOT NULL REFERENCES empresa_firma_cliente(id) ON DELETE CASCADE,
    activo      BOOLEAN    NOT NULL DEFAULT TRUE,
    created_by  INT,
    created_at  TIMESTAMP  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_firma_cliente
    ON usuario_firma_cliente (usuario_id, vinculo_id);

CREATE INDEX IF NOT EXISTS idx_usuario_firma_cliente_usuario
    ON usuario_firma_cliente (usuario_id) WHERE activo;
