-- ── V161: presentación que se compra pero no se vende ───────────────────────
--
-- En el form de producto, "Forma de venta" permite vender por unidad, por
-- empaque completo o ambos. Hasta hoy toda presentación activa salía en el
-- POS: no había forma de decir "la caja la compro, pero solo vendo suelto".
--
-- se_vende = false → la presentación sigue sirviendo para comprar (y convertir
-- cantidades), pero el POS no la ofrece. Las existentes quedan en true, que es
-- como se comportaban.

ALTER TABLE producto_presentacion
    ADD COLUMN IF NOT EXISTS se_vende BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN producto_presentacion.se_vende IS
    'false = solo para comprar: el POS no la ofrece. true (por defecto) = también se vende.';
