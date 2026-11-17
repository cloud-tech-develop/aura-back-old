-- V250 — Servicio técnico F2–F4 (docs/PLAN_SERVICIO_TECNICO.md).
--
-- F2: abonos (cada uno es un recibo de caja que queda como anticipo del cliente) y
--     entrega con factura de venta (se cruza con los abonos).
-- F4: enlace público para que el cliente consulte el estado y apruebe el presupuesto.
-- Idempotente.

-- Producto (servicio) con el que se facturan la mano de obra y los repuestos que no
-- están en el inventario.
ALTER TABLE servicio_tecnico_config ADD COLUMN IF NOT EXISTS producto_servicio_id BIGINT REFERENCES producto(id);

ALTER TABLE servicio_orden ADD COLUMN IF NOT EXISTS factura_venta_id BIGINT;
ALTER TABLE servicio_orden ADD COLUMN IF NOT EXISTS token_publico VARCHAR(40);
ALTER TABLE servicio_orden ADD COLUMN IF NOT EXISTS aprobado_cliente_at TIMESTAMP;

UPDATE servicio_orden
   SET token_publico = md5(random()::text || clock_timestamp()::text || id::text)
 WHERE token_publico IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_servicio_orden_token ON servicio_orden (token_publico);

-- ── Abonos ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS servicio_orden_abono (
    id              BIGSERIAL     PRIMARY KEY,
    empresa_id      INT           NOT NULL,
    orden_id        BIGINT        NOT NULL,
    recibo_caja_id  BIGINT        NOT NULL,
    anticipo_id     BIGINT,
    monto           NUMERIC(18,2) NOT NULL CHECK (monto > 0),
    metodo_pago     VARCHAR(40)   NOT NULL,
    estado          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'ANULADO')),
    usuario_id      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT fk_servicio_abono_orden FOREIGN KEY (orden_id, empresa_id)
        REFERENCES servicio_orden (id, empresa_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_servicio_abono_orden ON servicio_orden_abono (orden_id);
