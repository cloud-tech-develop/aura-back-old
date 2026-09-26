-- ── V164: lotes que cuadran con el inventario (F1) y compra que los crea (F2) ─
--
-- Hasta hoy el lote era un número aparte: la compra lo dejaba en null, la
-- pantalla de Lotes lo creaba con stock sin mover inventario ni kardex, y nada
-- garantizaba que la suma de los lotes fuera el stock. Desde esta migración,
-- para un producto con maneja_lotes:
--
--     Σ lote.stock_actual (activos) = inventario.stock_actual   por sucursal
--
-- y el único que mueve lotes es LoteStockService, dentro de la misma
-- transacción que mueve el inventario.
--
-- Plan: docs/PLAN_LOTES_SERIALES_CONSUMO_INTERNO.md (F1, F2).

ALTER TABLE lote ADD COLUMN IF NOT EXISTS empresa_id        INTEGER;
ALTER TABLE lote ADD COLUMN IF NOT EXISTS fecha_fabricacion DATE;
-- Línea de compra que creó el lote (la primera, si luego otra compra suma).
ALTER TABLE lote ADD COLUMN IF NOT EXISTS compra_detalle_id BIGINT;
ALTER TABLE lote ADD COLUMN IF NOT EXISTS created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE lote l
   SET empresa_id = s.empresa_id
  FROM sucursal s
 WHERE s.id = l.sucursal_id
   AND l.empresa_id IS NULL;

-- Salida por vencimiento (FEFO).
CREATE INDEX IF NOT EXISTS idx_lote_fefo
    ON lote (producto_id, sucursal_id, fecha_vencimiento);

-- Un código de lote no se repite para el mismo producto en la misma sucursal:
-- una segunda compra del mismo lote SUMA. Si hay duplicados viejos no se crea
-- el índice (el servicio igual busca el primero) y se deja el aviso.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM lote
         GROUP BY producto_id, sucursal_id, UPPER(TRIM(codigo_lote))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE NOTICE 'V164: hay lotes con código repetido por producto y sucursal; no se crea uq_lote_codigo';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_lote_codigo
            ON lote (producto_id, sucursal_id, UPPER(TRIM(codigo_lote)));
    END IF;
END $$;

-- De qué lotes entró (compra) o salió (nota crédito) cada línea de compra.
-- Anular y editar reversan exactamente esto.
CREATE TABLE IF NOT EXISTS compra_detalle_lote (
    id                 BIGSERIAL     PRIMARY KEY,
    compra_detalle_id  BIGINT        NOT NULL,
    lote_id            BIGINT        NOT NULL,
    -- Siempre positiva y en unidad base; el sentido lo da el documento.
    cantidad_base      NUMERIC(18,6) NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_compra_detalle_lote_detalle FOREIGN KEY (compra_detalle_id) REFERENCES compra_detalle(id),
    CONSTRAINT fk_compra_detalle_lote_lote    FOREIGN KEY (lote_id)           REFERENCES lote(id)
);

CREATE INDEX IF NOT EXISTS idx_compra_detalle_lote_detalle ON compra_detalle_lote (compra_detalle_id);
CREATE INDEX IF NOT EXISTS idx_compra_detalle_lote_lote    ON compra_detalle_lote (lote_id);

-- La pantalla de Lotes solo corrige código y vencimiento; cada corrección
-- deja rastro de quién, cuándo y por qué.
CREATE TABLE IF NOT EXISTS lote_ajuste (
    id              BIGSERIAL    PRIMARY KEY,
    lote_id         BIGINT       NOT NULL,
    empresa_id      INTEGER      NOT NULL,
    usuario_id      BIGINT,
    campo           VARCHAR(40)  NOT NULL,
    valor_anterior  VARCHAR(100),
    valor_nuevo     VARCHAR(100),
    motivo          VARCHAR(300) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_lote_ajuste_lote FOREIGN KEY (lote_id) REFERENCES lote(id)
);

CREATE INDEX IF NOT EXISTS idx_lote_ajuste_lote ON lote_ajuste (lote_id);

-- Cuadre inicial, una sola vez: el stock de un producto con lotes que no está
-- en ningún lote pasa a "SIN-LOTE" (sin vencimiento, sale de último). Si los
-- lotes suman MÁS que el inventario no se toca nada: lo muestra el diagnóstico
-- docs/sql/f0_diagnostico_lotes_seriales.sql.
CREATE TABLE IF NOT EXISTS migracion_datos_aplicada (
    clave        VARCHAR(80) PRIMARY KEY,
    aplicada_en  TIMESTAMP   NOT NULL DEFAULT now(),
    detalle      TEXT
);

DO $$
DECLARE
    v_creados     INTEGER := 0;
    v_ajustados   INTEGER := 0;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM migracion_datos_aplicada WHERE clave = 'V164_cuadre_sin_lote') THEN

        WITH diferencia AS (
            SELECT p.id AS producto_id, i.sucursal_id, p.empresa_id, p.costo,
                   i.stock_actual - COALESCE((SELECT SUM(l.stock_actual) FROM lote l
                                               WHERE l.producto_id = p.id
                                                 AND l.sucursal_id = i.sucursal_id
                                                 AND COALESCE(l.activo, true)), 0) AS falta
              FROM producto p
              JOIN inventario i ON i.producto_id = p.id
             WHERE p.maneja_lotes = true
               AND p.deleted_at IS NULL
        ),
        ajustados AS (
            UPDATE lote l
               SET stock_actual = l.stock_actual + d.falta,
                   activo = true
              FROM diferencia d
             WHERE d.falta > 0
               AND l.producto_id = d.producto_id
               AND l.sucursal_id = d.sucursal_id
               AND l.codigo_lote = 'SIN-LOTE'
            RETURNING l.id
        )
        SELECT COUNT(*) INTO v_ajustados FROM ajustados;

        INSERT INTO lote (producto_id, sucursal_id, empresa_id, codigo_lote, fecha_vencimiento,
                          stock_actual, costo_unitario, activo, created_at)
        SELECT p.id, i.sucursal_id, p.empresa_id, 'SIN-LOTE', NULL,
               i.stock_actual - COALESCE((SELECT SUM(l.stock_actual) FROM lote l
                                           WHERE l.producto_id = p.id
                                             AND l.sucursal_id = i.sucursal_id
                                             AND COALESCE(l.activo, true)), 0),
               COALESCE(p.costo, 0), true, CURRENT_TIMESTAMP
          FROM producto p
          JOIN inventario i ON i.producto_id = p.id
         WHERE p.maneja_lotes = true
           AND p.deleted_at IS NULL
           AND NOT EXISTS (SELECT 1 FROM lote l
                            WHERE l.producto_id = p.id
                              AND l.sucursal_id = i.sucursal_id
                              AND l.codigo_lote = 'SIN-LOTE')
           AND i.stock_actual - COALESCE((SELECT SUM(l.stock_actual) FROM lote l
                                           WHERE l.producto_id = p.id
                                             AND l.sucursal_id = i.sucursal_id
                                             AND COALESCE(l.activo, true)), 0) > 0;

        GET DIAGNOSTICS v_creados = ROW_COUNT;

        INSERT INTO migracion_datos_aplicada (clave, detalle)
        VALUES ('V164_cuadre_sin_lote',
                v_creados || ' lotes SIN-LOTE creados, ' || v_ajustados || ' completados');
    END IF;
END $$;
