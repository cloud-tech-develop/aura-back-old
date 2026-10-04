-- V195 — Facturación ERP fuera del POS (FV0/FV1 de docs/PLAN_FACTURACION.md).
--
-- La factura emitida vive en venta/venta_detalle (tipo_documento = 'FACTURA')
-- para reutilizar stock, kardex, costo, cartera, asiento, FE y anulación. Lo
-- que es solo de Facturación vive aquí:
--   - condicion_pago: Contado, 30, 60, 90 días… el vencimiento se calcula.
--   - factura_venta: el borrador (no consume consecutivo, ni stock, ni asiento)
--     y, ya emitida, los datos de ERP de la factura (orden de compra del
--     cliente, vendedor, condición) con el enlace a su venta.
--   - venta_detalle.descripcion: la línea de servicio lleva su propio texto
--     ("Mantenimiento preventivo octubre") sobre el producto de servicio.
--   - Submódulo ventas.facturas, con permiso propio (no depende del POS).

-- ── Condiciones de pago ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS condicion_pago (
    id          BIGSERIAL    PRIMARY KEY,
    empresa_id  INT          NOT NULL,
    nombre      VARCHAR(80)  NOT NULL,
    dias        INT          NOT NULL DEFAULT 0,
    activa      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_condicion_pago_empresa_nombre
    ON condicion_pago (empresa_id, nombre);

INSERT INTO condicion_pago (empresa_id, nombre, dias)
SELECT e.id, v.nombre, v.dias
FROM empresa e
CROSS JOIN (VALUES ('Contado', 0), ('Crédito 30 días', 30), ('Crédito 60 días', 60), ('Crédito 90 días', 90))
    AS v(nombre, dias)
ON CONFLICT (empresa_id, nombre) DO NOTHING;

-- ── Borrador y datos de la factura ──────────────────────────────────
CREATE TABLE IF NOT EXISTS factura_venta (
    id                    BIGSERIAL      PRIMARY KEY,
    empresa_id            INT            NOT NULL,
    sucursal_id           INT            NOT NULL,
    bodega_id             BIGINT,
    cliente_id            BIGINT         NOT NULL,
    vendedor_id           INT,
    condicion_pago_id     BIGINT         REFERENCES condicion_pago(id),
    -- CREDITO: queda en cartera. CONTADO: entra por metodo_pago.
    forma_pago            VARCHAR(10)    NOT NULL DEFAULT 'CREDITO',
    -- Contado: EFECTIVO (caja general, sin turno) o TRANSFERENCIA/CONSIGNACION/TARJETA (banco).
    metodo_pago           VARCHAR(30),
    cuenta_bancaria_id    BIGINT,
    fecha_vencimiento     DATE,
    orden_compra          VARCHAR(60),
    notas                 VARCHAR(1000),
    -- BORRADOR | EMITIDA | ANULADA
    estado                VARCHAR(10)    NOT NULL DEFAULT 'BORRADOR',
    venta_id              BIGINT,
    subtotal              NUMERIC(15,2)  NOT NULL DEFAULT 0,
    descuento_total       NUMERIC(15,2)  NOT NULL DEFAULT 0,
    impuestos_total       NUMERIC(15,2)  NOT NULL DEFAULT 0,
    total                 NUMERIC(15,2)  NOT NULL DEFAULT 0,
    usuario_id            INT,
    emitida_por           INT,
    emitida_at            TIMESTAMP,
    created_at            TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_factura_venta_empresa ON factura_venta (empresa_id, estado);
CREATE UNIQUE INDEX IF NOT EXISTS uq_factura_venta_venta ON factura_venta (venta_id) WHERE venta_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS factura_venta_detalle (
    id                        BIGSERIAL      PRIMARY KEY,
    factura_venta_id          BIGINT         NOT NULL REFERENCES factura_venta(id) ON DELETE CASCADE,
    producto_id               BIGINT         NOT NULL,
    producto_presentacion_id  BIGINT,
    descripcion               VARCHAR(500),
    cantidad                  NUMERIC(18,6)  NOT NULL,
    precio_unitario           NUMERIC(15,2)  NOT NULL,
    descuento_valor           NUMERIC(15,2)  NOT NULL DEFAULT 0,
    impuesto_porcentaje       NUMERIC(6,2)   NOT NULL DEFAULT 0,
    impuesto_valor            NUMERIC(15,2)  NOT NULL DEFAULT 0,
    subtotal_linea            NUMERIC(15,2)  NOT NULL DEFAULT 0,
    orden                     INT            NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_factura_venta_detalle_factura ON factura_venta_detalle (factura_venta_id);

-- ── Texto propio de la línea ────────────────────────────────────────
ALTER TABLE venta_detalle ADD COLUMN IF NOT EXISTS descripcion VARCHAR(500);

-- ── Submódulo Ventas › Facturas ─────────────────────────────────────
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Facturas', 'facturas',
       'Factura de venta fuera del punto de venta: borrador, crédito o contado, productos y servicios',
       TRUE, 0, now(), now()
FROM modulos m
WHERE m.codigo = 'ventas'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = 'facturas');

INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, TRUE, now(), now()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id AND m.codigo = 'ventas'
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = TRUE
WHERE s.codigo = 'facturas'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );
