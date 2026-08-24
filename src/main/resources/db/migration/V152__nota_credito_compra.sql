-- ── V152: la nota crédito de compra es un documento NEGATIVO ───────────────
--
-- Hasta ahora `tipo_documento = 'NOTA_CREDITO'` era solo una etiqueta: la NC se
-- registraba idéntica a una factura de compra — SUMABA stock, actualizaba el
-- costo del producto, creaba una cuenta por pagar nueva y generaba un asiento
-- débito a inventario. Justo al revés de lo que hace una nota crédito, que
-- existe para anular mercancía que no llegó.
--
-- Dos columnas para arreglarlo:
--
--   · compra_origen_id — la factura de compra que la NC corrige. Sin ella no se
--     puede validar que no se acredite más cantidad de la comprada, ni saber
--     contra qué cuenta por pagar cruzarla.
--
--   · destino_nota_credito — qué pasa con la plata:
--       CRUCE_CXP          → baja la deuda de la factura origen (compra a crédito)
--       DEVOLUCION_DINERO  → el proveedor devuelve la plata (entra a caja/banco)
--       SALDO_A_FAVOR      → queda crédito con el proveedor para compras futuras
--
-- Los importes y las cantidades de la NC se guardan en NEGATIVO. Así toda suma
-- que ya existía sobre `compra` (dashboard, estado de cuenta del proveedor,
-- kardex) se neta sola, sin que cada consumidor tenga que conocer el tipo de
-- documento.

ALTER TABLE compra ADD COLUMN IF NOT EXISTS compra_origen_id BIGINT;
ALTER TABLE compra ADD COLUMN IF NOT EXISTS destino_nota_credito VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_compra_origen'
    ) THEN
        ALTER TABLE compra
            ADD CONSTRAINT fk_compra_origen
            FOREIGN KEY (compra_origen_id) REFERENCES compra (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_compra_origen
    ON compra (compra_origen_id) WHERE compra_origen_id IS NOT NULL;
