-- V189 — Cadena documental: relaciones entre documentos (fase D0).
--
-- Hasta hoy la trazabilidad entre documentos vivía en campos sueltos
-- (compra.compra_origen_id, devolucion.venta_id, pedido.venta_id) y ninguno
-- guardaba CUÁNTO se aplicó de un documento a otro. Sin eso no se puede saber
-- qué le queda pendiente por facturar/recibir/devolver a una cotización, OC o
-- venta, ni evitar que una línea se consuma dos veces.
--
-- Esta tabla es la base única de la cadena: cada fila dice que una línea (o un
-- documento) ORIGEN se aplicó a un documento DESTINO por una cantidad y/o un
-- valor. El pendiente de una línea origen = cantidad original − Σ aplicado
-- vigente. Al anular el destino, su(s) relación(es) pasan a ANULADA y el
-- pendiente del origen se libera.
--
--   Ventas:  Cotización → Pedido → Venta → Devolución/NC/ND → Recibo
--   Compras: Orden de compra → Remisión → Compra → NC/ND → Egreso

CREATE TABLE IF NOT EXISTS documento_relacion (
    id               BIGSERIAL     PRIMARY KEY,
    empresa_id       INT           NOT NULL,
    -- Tipo lógico del documento (COTIZACION, PEDIDO, VENTA, DEVOLUCION,
    -- NOTA_VENTA, ORDEN_COMPRA, REMISION_COMPRA, COMPRA, NOTA_COMPRA,
    -- RECIBO, EGRESO, ...). Se compara siempre en mayúsculas.
    origen_tipo      VARCHAR(30)   NOT NULL,
    origen_id        BIGINT        NOT NULL,
    -- Línea del documento origen; NULL cuando la relación es a nivel documento.
    origen_linea_id  BIGINT,
    destino_tipo     VARCHAR(30)   NOT NULL,
    destino_id       BIGINT        NOT NULL,
    destino_linea_id BIGINT,
    -- Cantidad aplicada (en la unidad de la línea origen) y/o valor aplicado.
    cantidad         NUMERIC(18,6),
    valor            NUMERIC(18,2),
    -- VIGENTE | ANULADA. Solo las VIGENTE cuentan para el pendiente.
    estado           VARCHAR(10)   NOT NULL DEFAULT 'VIGENTE',
    created_by       INT,
    created_at       TIMESTAMP     NOT NULL DEFAULT now()
);

-- Hacia adelante: "¿a qué documentos se aplicó este origen?" y cálculo del
-- pendiente por línea origen.
CREATE INDEX IF NOT EXISTS idx_documento_relacion_origen
    ON documento_relacion (empresa_id, origen_tipo, origen_id);

CREATE INDEX IF NOT EXISTS idx_documento_relacion_origen_linea
    ON documento_relacion (origen_tipo, origen_linea_id)
    WHERE origen_linea_id IS NOT NULL;

-- Hacia atrás: "¿de qué documentos viene este destino?" y anulación por destino.
CREATE INDEX IF NOT EXISTS idx_documento_relacion_destino
    ON documento_relacion (empresa_id, destino_tipo, destino_id);
