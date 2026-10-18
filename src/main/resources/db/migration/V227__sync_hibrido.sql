-- V227 — Modo híbrido (docs/MODO_HIBRIDO.md): servidor local por tienda que
-- trabaja sin internet y se sincroniza con la nube.
--
-- Las cinco tablas existen en las dos bases (mismo esquema), pero cada lado usa
-- las suyas:
--   nube  : sync_nodo, sync_recibido, sync_id_map
--   local : sync_outbox, sync_estado

-- Servidor local registrado en la nube. El token se guarda como SHA-256: el
-- valor en claro se muestra una sola vez al crearlo.
CREATE TABLE IF NOT EXISTS sync_nodo (
    id               BIGSERIAL PRIMARY KEY,
    empresa_id       INTEGER      NOT NULL REFERENCES empresa (id),
    sucursal_id      INTEGER      REFERENCES sucursal (id),
    codigo           VARCHAR(40)  NOT NULL,
    nombre           VARCHAR(120) NOT NULL,
    -- Serie propia de las ventas del nodo: así sus consecutivos nunca chocan
    -- con los que la nube saca para la misma sucursal.
    prefijo_ventas   VARCHAR(20),
    token_hash       VARCHAR(64)  NOT NULL,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_contacto  TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_sync_nodo_codigo UNIQUE (empresa_id, codigo),
    CONSTRAINT uq_sync_nodo_token UNIQUE (token_hash)
);

-- Comandos que la nube ya recibió de un nodo. El uuid hace el envío idempotente:
-- si el nodo reintenta un lote que la nube alcanzó a procesar, no se duplica.
CREATE TABLE IF NOT EXISTS sync_recibido (
    id            BIGSERIAL PRIMARY KEY,
    nodo_id       BIGINT       NOT NULL REFERENCES sync_nodo (id),
    uuid          VARCHAR(36)  NOT NULL,
    tipo          VARCHAR(40)  NOT NULL,
    id_local      BIGINT,
    estado        VARCHAR(10)  NOT NULL,
    respuesta     TEXT,
    error         TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_sync_recibido_uuid UNIQUE (uuid)
);
CREATE INDEX IF NOT EXISTS idx_sync_recibido_nodo ON sync_recibido (nodo_id, created_at);

-- Equivalencia entre el id que el registro tiene en el nodo y el que recibió en
-- la nube al reproducirse (venta, turno, tercero...).
CREATE TABLE IF NOT EXISTS sync_id_map (
    nodo_id     BIGINT      NOT NULL REFERENCES sync_nodo (id),
    tabla       VARCHAR(60) NOT NULL,
    id_local    BIGINT      NOT NULL,
    id_nube     BIGINT      NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (nodo_id, tabla, id_local)
);

-- Cola de salida del nodo: cada operación que debe llegar a la nube, en orden.
CREATE TABLE IF NOT EXISTS sync_outbox (
    id            BIGSERIAL PRIMARY KEY,
    uuid          VARCHAR(36)  NOT NULL,
    tipo          VARCHAR(40)  NOT NULL,
    id_local      BIGINT,
    empresa_id    INTEGER      NOT NULL,
    usuario_id    BIGINT,
    fecha         TIMESTAMP    NOT NULL,
    payload       TEXT         NOT NULL,
    -- PENDIENTE · ENVIADO · ERROR · DESCARTADO
    estado        VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',
    intentos      INTEGER      NOT NULL DEFAULT 0,
    ultimo_error  TEXT,
    respuesta     TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    enviado_at    TIMESTAMP,
    CONSTRAINT uq_sync_outbox_uuid UNIQUE (uuid)
);
CREATE INDEX IF NOT EXISTS idx_sync_outbox_pendiente ON sync_outbox (id) WHERE estado IN ('PENDIENTE', 'ERROR');

-- Estado del nodo: identidad recibida de la nube, última sincronización y pisos
-- de consecutivos.
CREATE TABLE IF NOT EXISTS sync_estado (
    clave       VARCHAR(120) PRIMARY KEY,
    valor       TEXT,
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);
