-- ── V160: registro de "Pasar a unidad" (cambio de unidad base de un producto) ──
--
-- Un producto cargado con la unidad grande como base (ARROZ base PACA con la
-- presentación UNIDAD que contiene 0,04) se puede pasar a la unidad pequeña:
-- el inventario, los lotes y el kardex se multiplican por N (25) y los costos
-- se dividen por N; la base vieja queda como presentación "PACA contiene 25".
--
-- Los documentos sin presentación (compra, merma, obsequio, traslado) guardan
-- sus cantidades en la unidad que había cuando se hicieron. Para que anular o
-- editar uno viejo no devuelva 5 unidades donde entraron 5 pacas, se guarda el
-- último id de cada documento al momento del cambio: si el documento es igual o
-- anterior a ese id y trae el producto, el servicio no deja anularlo ni editarlo.
-- Venta y devolución no lo necesitan: sus líneas se reapuntan a la presentación.

CREATE TABLE IF NOT EXISTS producto_cambio_unidad (
    id                       BIGSERIAL     PRIMARY KEY,
    empresa_id               INTEGER       NOT NULL,
    producto_id              BIGINT        NOT NULL REFERENCES producto (id),
    factor                   NUMERIC(18,6) NOT NULL,
    unidad_anterior_id       BIGINT,
    unidad_nueva_id          BIGINT,
    presentacion_grande_id   BIGINT        REFERENCES producto_presentacion (id),
    presentacion_pequena_id  BIGINT        REFERENCES producto_presentacion (id),
    ultimo_compra_id         BIGINT        NOT NULL DEFAULT 0,
    ultimo_merma_id          BIGINT        NOT NULL DEFAULT 0,
    ultimo_obsequio_id       BIGINT        NOT NULL DEFAULT 0,
    ultimo_traslado_id       BIGINT        NOT NULL DEFAULT 0,
    usuario_id               BIGINT,
    created_at               TIMESTAMP     NOT NULL DEFAULT now()
);

COMMENT ON TABLE producto_cambio_unidad IS
    'Cada "Pasar a unidad": factor N aplicado y últimos ids de documentos sin presentación; los anteriores no se anulan ni editan para ese producto.';

CREATE INDEX IF NOT EXISTS idx_producto_cambio_unidad_producto
    ON producto_cambio_unidad (producto_id);
