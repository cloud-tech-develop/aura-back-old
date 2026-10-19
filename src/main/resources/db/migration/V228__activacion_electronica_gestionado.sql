-- V228 — Activación de los documentos electrónicos de un cliente gestionado
-- (docs/PLAN_FIRMA_CONTABLE.md §10).
--
-- La firma pide que se le habilite a su cliente la factura electrónica, la nómina
-- electrónica o el documento soporte. Las credenciales de Factus las pone solo la
-- plataforma (Factus se las entrega a Aura): la solicitud le llega a la campana del
-- panel de plataforma y queda ATENDIDA sola cuando se guardan las credenciales con
-- la facturación electrónica activa.

CREATE TABLE IF NOT EXISTS solicitud_activacion_electronica (
    id                  BIGSERIAL     PRIMARY KEY,
    cliente_empresa_id  INT           NOT NULL REFERENCES empresa(id),
    firma_empresa_id    INT           NOT NULL REFERENCES empresa(id),
    -- Lista separada por comas: FACTURA_VENTA, NOMINA_ELECTRONICA, DOCUMENTO_SOPORTE.
    servicios           VARCHAR(120)  NOT NULL,
    observacion         VARCHAR(300),
    -- PENDIENTE | ATENDIDA | RECHAZADA | CANCELADA
    estado              VARCHAR(15)   NOT NULL DEFAULT 'PENDIENTE',
    solicitado_por      INT,
    solicitado_at       TIMESTAMP     NOT NULL DEFAULT now(),
    respondido_por      INT,
    respondido_at       TIMESTAMP,
    motivo              VARCHAR(300)
);

-- Un cliente tiene como máximo una solicitud pendiente.
CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitud_activacion_pendiente
    ON solicitud_activacion_electronica (cliente_empresa_id) WHERE estado = 'PENDIENTE';

CREATE INDEX IF NOT EXISTS idx_solicitud_activacion_firma
    ON solicitud_activacion_electronica (firma_empresa_id, estado);
