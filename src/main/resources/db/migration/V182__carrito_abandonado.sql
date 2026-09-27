-- V182 — Carritos del POS que se vaciaron sin vender.
--
-- El carrito vive solo en el navegador del cajero: si se vaciaba sin cobrar no
-- quedaba rastro de qué productos tenía ni cuánto tiempo estuvo armado. El POS
-- ahora reporta cada carrito que el cajero vacía o cuya pestaña cierra con
-- productos. Al vender o al convertirlo en cotización NO se registra: eso no
-- es abandono.
--
-- Se guardan todos, con su duración; el reporte decide desde cuántos minutos
-- cuenta como abandono (por defecto 5), así cambiar el umbral no pierde datos.

CREATE TABLE IF NOT EXISTS carrito_abandonado (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         INT           NOT NULL,
    sucursal_id        INT,
    turno_caja_id      BIGINT,
    usuario_id         INT,
    cliente_id         BIGINT,
    -- VACIADO (botón vaciar) | PESTANA_CERRADA (cerró la orden)
    motivo             VARCHAR(20)   NOT NULL,
    iniciado_at        TIMESTAMP     NOT NULL,
    vaciado_at         TIMESTAMP     NOT NULL,
    duracion_segundos  INT           NOT NULL,
    items              INT           NOT NULL,
    total              NUMERIC(15,2) NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_carrito_abandonado_empresa_fecha
    ON carrito_abandonado (empresa_id, vaciado_at);

CREATE TABLE IF NOT EXISTS carrito_abandonado_item (
    id              BIGSERIAL PRIMARY KEY,
    carrito_id      BIGINT        NOT NULL REFERENCES carrito_abandonado(id) ON DELETE CASCADE,
    producto_id     BIGINT,
    presentacion_id BIGINT,
    nombre          VARCHAR(255)  NOT NULL,
    cantidad        NUMERIC(15,4) NOT NULL,
    precio          NUMERIC(15,2) NOT NULL,
    subtotal        NUMERIC(15,2) NOT NULL,
    agregado_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_carrito_abandonado_item_carrito
    ON carrito_abandonado_item (carrito_id);
