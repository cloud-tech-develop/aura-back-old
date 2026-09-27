-- ── V165: las salidas descuentan lotes (F3) ────────────────────────────────
--
-- V164 dejó el lote cuadrado con el inventario y la compra creándolos. Desde
-- aquí toda salida (venta, merma, obsequio, consumo interno, traslado,
-- devolución, reconteo, ajuste) saca de los lotes: la que el usuario eligió o,
-- si no eligió, la que vence primero (FEFO).
--
-- documento_lote guarda de qué lote salió (o a cuál entró) cada línea, para que
-- anular devuelva exactamente eso. Es una sola tabla para todos los documentos,
-- igual que inventario_consumo_componente: `detalle_id` apunta a la línea del
-- documento que indica `origen`, por eso no lleva FK.
--
-- Plan: docs/PLAN_LOTES_SERIALES_CONSUMO_INTERNO.md (F3).

CREATE TABLE IF NOT EXISTS documento_lote (
    id             BIGSERIAL     PRIMARY KEY,
    -- VENTA, VENTA_COMPONENTE, MERMA, OBSEQUIO, CONSUMO_INTERNO, COMPONENTE,
    -- TRASLADO_SALIDA, TRASLADO_ENTRADA, DEVOLUCION, DEVOLUCION_CAMBIO,
    -- RECONTEO, AJUSTE_INVENTARIO
    origen         VARCHAR(30)   NOT NULL,
    detalle_id     BIGINT        NOT NULL,
    lote_id        BIGINT        NOT NULL,
    -- Siempre positiva y en unidad base; el sentido lo da el origen.
    cantidad_base  NUMERIC(18,6) NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_documento_lote_lote FOREIGN KEY (lote_id) REFERENCES lote(id)
);

CREATE INDEX IF NOT EXISTS idx_documento_lote_detalle ON documento_lote (origen, detalle_id);
CREATE INDEX IF NOT EXISTS idx_documento_lote_lote    ON documento_lote (lote_id);

-- Reglas por empresa.
-- Bloquear vencidos: la venta, el obsequio y el consumo interno no sacan de un
-- lote vencido (la merma y la devolución al proveedor sí).
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS lotes_bloquear_vencidos BOOLEAN NOT NULL DEFAULT TRUE;
-- Días antes del vencimiento en que el POS y el dashboard empiezan a avisar.
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS lotes_dias_alerta INTEGER NOT NULL DEFAULT 30;
