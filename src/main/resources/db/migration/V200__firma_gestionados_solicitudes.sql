-- V200 — Firma contable, segunda etapa (docs/PLAN_FIRMA_CONTABLE.md §9, F8).
--
-- · Modalidad del vínculo: CLIENTE_PAGA (el cliente usa Aura y paga su licencia) o
--   GESTIONADO (la firma creó la empresa y la paga por contrato aparte).
-- · Cupo de clientes gestionados por firma (10 al inicio; la plataforma lo sube).
-- · Solicitudes: un cliente pide a una firma que le lleve la contabilidad.
--
-- Regla del día 1: los vínculos que ya existen quedan CLIENTE_PAGA; nada cambia.

ALTER TABLE empresa_firma_cliente
    ADD COLUMN IF NOT EXISTS modalidad VARCHAR(20) NOT NULL DEFAULT 'CLIENTE_PAGA';

ALTER TABLE empresa ADD COLUMN IF NOT EXISTS cupo_gestionados INT NOT NULL DEFAULT 10;

CREATE TABLE IF NOT EXISTS solicitud_firma_cliente (
    id                  BIGSERIAL     PRIMARY KEY,
    cliente_empresa_id  INT           NOT NULL REFERENCES empresa(id),
    firma_empresa_id    INT           NOT NULL REFERENCES empresa(id),
    -- PENDIENTE | ACEPTADA | RECHAZADA | CANCELADA
    estado              VARCHAR(15)   NOT NULL DEFAULT 'PENDIENTE',
    solicitado_por      INT,
    solicitado_at       TIMESTAMP     NOT NULL DEFAULT now(),
    respondido_por      INT,
    respondido_at       TIMESTAMP,
    motivo              VARCHAR(300),
    CONSTRAINT ck_solicitud_firma_distintas CHECK (firma_empresa_id <> cliente_empresa_id)
);

-- Un cliente tiene como máximo una solicitud pendiente.
CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitud_firma_pendiente
    ON solicitud_firma_cliente (cliente_empresa_id) WHERE estado = 'PENDIENTE';

CREATE INDEX IF NOT EXISTS idx_solicitud_firma_firma
    ON solicitud_firma_cliente (firma_empresa_id, estado);
