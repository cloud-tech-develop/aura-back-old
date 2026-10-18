-- V226 — Historial del costo de cada producto.
--
-- producto.costo es el costo promedio ponderado (V184) y cada movimiento lo
-- sobrescribe: no quedaba rastro de cuánto valía antes. Cuando los socios
-- liquidan entre ellos (o el contador revisa la utilidad de un período) necesitan
-- saber a qué costo estaba la mercancía en cada momento y qué documento lo movió.
--
-- Una fila por cada documento que toca el costo: compra, nota crédito de compra,
-- su anulación o edición, saldo inicial importado, ajuste manual en la ficha del
-- producto, costo aplicado desde la receta e importación del catálogo.
-- Las compras anteriores a esta migración se ven igual: el historial de compras
-- del producto se lee directo de compra_detalle.

CREATE TABLE IF NOT EXISTS producto_costo_historial (
    id               BIGSERIAL PRIMARY KEY,
    empresa_id       INT            NOT NULL REFERENCES empresa(id),
    producto_id      BIGINT         NOT NULL REFERENCES producto(id),
    fecha            TIMESTAMP      NOT NULL DEFAULT now(),
    origen           VARCHAR(30)    NOT NULL,
    documento_id     BIGINT,
    documento_numero VARCHAR(60),
    tercero_id       BIGINT,
    cantidad         NUMERIC(38,6),
    costo_entrada    NUMERIC(38,6),
    stock_antes      NUMERIC(38,6),
    stock_despues    NUMERIC(38,6),
    costo_anterior   NUMERIC(38,6),
    costo_nuevo      NUMERIC(38,6)  NOT NULL,
    usuario_id       BIGINT,
    observacion      VARCHAR(200)
);

CREATE INDEX IF NOT EXISTS ix_producto_costo_historial_producto
    ON producto_costo_historial (producto_id, fecha DESC);
