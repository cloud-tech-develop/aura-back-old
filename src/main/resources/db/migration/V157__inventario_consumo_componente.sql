-- ── V157: lo que consumió un producto con receta fuera de la venta ──────────
--
-- Una merma o un obsequio de "pan" no saca pan del inventario: el pan con
-- receta no tiene stock propio, lo que sale es su harina, su levadura… igual
-- que cuando se vende. Hasta hoy merma y obsequio descontaban el padre y
-- fallaban con "no tiene inventario en esta sucursal".
--
-- Esta tabla guarda QUÉ salió realmente por cada línea del documento. Anular
-- tiene que devolver eso, no lo que diga la receta el día de la anulación: si
-- entre tanto cambiaron la receta, releerla devolvería cantidades distintas a
-- las que se descontaron. También alimenta el asiento, que acredita el
-- inventario de cada componente y no el del padre.
--
-- `detalle_id` apunta a merma_detalle.id u obsequio_detalle.id según `origen`;
-- por eso no lleva FK. VENTA queda permitido para cuando la venta migre a este
-- mismo registro.

CREATE TABLE IF NOT EXISTS inventario_consumo_componente (
    id                BIGSERIAL PRIMARY KEY,
    empresa_id        INTEGER       NOT NULL,
    origen            VARCHAR(20)   NOT NULL,
    detalle_id        BIGINT        NOT NULL,
    producto_padre_id BIGINT        NOT NULL,
    producto_hijo_id  BIGINT        NOT NULL,
    -- En unidad base de stock del componente, ya multiplicada por la cantidad
    -- del padre.
    cantidad          NUMERIC(18,6) NOT NULL,
    -- Costo del componente congelado al consumir.
    costo_unitario    NUMERIC(18,6) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_consumo_componente_origen CHECK (origen IN ('MERMA', 'OBSEQUIO', 'VENTA')),
    CONSTRAINT fk_consumo_componente_empresa FOREIGN KEY (empresa_id)        REFERENCES empresa(id),
    CONSTRAINT fk_consumo_componente_padre   FOREIGN KEY (producto_padre_id) REFERENCES producto(id),
    CONSTRAINT fk_consumo_componente_hijo    FOREIGN KEY (producto_hijo_id)  REFERENCES producto(id)
);

-- Anular y contabilizar buscan siempre por la línea del documento.
CREATE INDEX IF NOT EXISTS idx_consumo_componente_detalle
    ON inventario_consumo_componente (origen, detalle_id);
