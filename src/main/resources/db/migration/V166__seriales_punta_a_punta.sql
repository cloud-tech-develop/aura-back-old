-- ── V166: seriales de punta a punta (F4) ───────────────────────────────────
--
-- Hasta hoy el serial se creaba a mano, sin compra de origen ni costo, la
-- venta solo lo usaba si alguien lo mandaba y nada garantizaba que los
-- seriales disponibles fueran el stock. Desde aquí, para un producto con
-- maneja_serial:
--
--     seriales DISPONIBLES de la sucursal = inventario.stock_actual
--
-- La compra los crea (uno por unidad), cada salida elige cuáles salen y
-- anular devuelve exactamente esos. documento_serial guarda el rastro, igual
-- que documento_lote; la venta sigue usando venta_detalle_serial.
--
-- Plan: docs/PLAN_LOTES_SERIALES_CONSUMO_INTERNO.md (F4).

ALTER TABLE serial_producto ADD COLUMN IF NOT EXISTS empresa_id             INTEGER;
ALTER TABLE serial_producto ADD COLUMN IF NOT EXISTS costo                  NUMERIC(15,2);
ALTER TABLE serial_producto ADD COLUMN IF NOT EXISTS fecha_ingreso          TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
-- Línea de compra que lo creó (null si se registró a mano sobre stock existente).
ALTER TABLE serial_producto ADD COLUMN IF NOT EXISTS compra_detalle_id      BIGINT;
-- Último documento por el que salió (VENTA, MERMA, OBSEQUIO…).
ALTER TABLE serial_producto ADD COLUMN IF NOT EXISTS documento_salida_tipo  VARCHAR(30);
ALTER TABLE serial_producto ADD COLUMN IF NOT EXISTS documento_salida_id    BIGINT;
ALTER TABLE serial_producto ADD COLUMN IF NOT EXISTS garantia_cliente_hasta DATE;

UPDATE serial_producto sp
   SET empresa_id = s.empresa_id
  FROM sucursal s
 WHERE s.id = sp.sucursal_id
   AND sp.empresa_id IS NULL;

-- El serial era único en TODA la base: una empresa bloqueaba el IMEI de otra.
-- Ahora es único por producto (sin distinguir mayúsculas ni espacios).
ALTER TABLE serial_producto DROP CONSTRAINT IF EXISTS serial_producto_serial_key;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM serial_producto
         GROUP BY producto_id, UPPER(TRIM(serial))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE NOTICE 'V166: hay seriales repetidos dentro de un producto; no se crea uq_serial_producto';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_serial_producto
            ON serial_producto (producto_id, UPPER(TRIM(serial)));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_serial_producto_disponible
    ON serial_producto (producto_id, sucursal_id, estado);

-- Meses de garantía que se le dan al cliente al vender (null = sin garantía).
ALTER TABLE producto ADD COLUMN IF NOT EXISTS meses_garantia INTEGER;

-- De qué documento entró o salió cada serial. `detalle_id` apunta a la línea
-- del documento de `origen`: COMPRA, NOTA_CREDITO_COMPRA, MERMA, OBSEQUIO,
-- CONSUMO_INTERNO, TRASLADO, DEVOLUCION.
CREATE TABLE IF NOT EXISTS documento_serial (
    id               BIGSERIAL    PRIMARY KEY,
    origen           VARCHAR(30)  NOT NULL,
    detalle_id       BIGINT       NOT NULL,
    serial_id        BIGINT       NOT NULL,
    -- Estado y sucursal antes del movimiento: anular los restaura.
    estado_anterior  VARCHAR(20),
    sucursal_anterior_id INTEGER,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_documento_serial_serial FOREIGN KEY (serial_id) REFERENCES serial_producto(id)
);

CREATE INDEX IF NOT EXISTS idx_documento_serial_detalle ON documento_serial (origen, detalle_id);
CREATE INDEX IF NOT EXISTS idx_documento_serial_serial  ON documento_serial (serial_id);
