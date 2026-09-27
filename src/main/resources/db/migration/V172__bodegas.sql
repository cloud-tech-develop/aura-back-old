-- ── V172: bodegas dentro de la sucursal ───────────────────────────────────
--
-- Hasta hoy el saldo vivía por sucursal: `inventario (sucursal_id, producto_id)`.
-- Eso alcanza para una tienda, no para un negocio con bodega principal, bodega
-- de averías, nevera, vitrina o bodega de obra dentro del mismo local. Y no
-- había a quién responsabilizar de un faltante.
--
-- A partir de aquí **el saldo vive en la bodega**. La sucursal sigue existiendo
-- (es la que factura, la que tiene caja y resolución), y su stock es la suma de
-- sus bodegas.
--
-- Estrategia para no romper nada:
--   1. Cada sucursal recibe una "Bodega Principal" (es_principal = TRUE).
--   2. Toda fila existente de inventario, kardex, lote, serial y de cada
--      documento se rellena con la bodega principal de su sucursal.
--   3. `sucursal_id` NO se elimina de ninguna tabla: queda como columna
--      derivada, para que todo reporte que hoy agrupa por sucursal siga dando
--      exactamente lo mismo. La bodega es una dimensión que se agrega, no un
--      reemplazo.
--   4. Cuando un documento no dice bodega, el backend resuelve la principal de
--      la sucursal (BodegaResolver). Operar sin bodegas sigue funcionando igual.
--
-- Plan: docs/PLAN_BODEGAS.md

-- ── 1. La bodega ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bodega (
    id                     BIGSERIAL    PRIMARY KEY,
    empresa_id             INTEGER      NOT NULL,
    sucursal_id            INTEGER      NOT NULL,
    codigo                 VARCHAR(20),
    nombre                 VARCHAR(80)  NOT NULL,
    -- Quién responde por el faltante. Opcional: una bodega puede no tenerlo.
    responsable_usuario_id INTEGER,
    -- La que se usa cuando el documento no dice cuál. Exactamente una por
    -- sucursal (ver índice único parcial más abajo).
    es_principal           BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Una bodega que no vende: averías, cuarentena, mercancía en tránsito.
    -- El POS no la ofrece; merma, traslado y reconteo sí.
    permite_venta          BOOLEAN      NOT NULL DEFAULT TRUE,
    ubicacion              VARCHAR(120),
    observacion            VARCHAR(300),
    activa                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP,

    CONSTRAINT fk_bodega_empresa     FOREIGN KEY (empresa_id)             REFERENCES empresa(id),
    CONSTRAINT fk_bodega_sucursal    FOREIGN KEY (sucursal_id)            REFERENCES sucursal(id),
    CONSTRAINT fk_bodega_responsable FOREIGN KEY (responsable_usuario_id) REFERENCES usuario(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_bodega_nombre
    ON bodega (sucursal_id, LOWER(nombre));

CREATE UNIQUE INDEX IF NOT EXISTS uq_bodega_codigo
    ON bodega (empresa_id, UPPER(codigo)) WHERE codigo IS NOT NULL;

-- Una sola principal por sucursal: es la que resuelve el backend cuando el
-- documento no trae bodega.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bodega_principal
    ON bodega (sucursal_id) WHERE es_principal;

CREATE INDEX IF NOT EXISTS idx_bodega_empresa ON bodega (empresa_id, activa);

-- ── 2. Una bodega principal por sucursal existente ────────────────────────
INSERT INTO bodega (empresa_id, sucursal_id, codigo, nombre, es_principal, permite_venta, activa)
SELECT s.empresa_id, s.id, 'BOD-' || s.id, 'Bodega Principal', TRUE, TRUE, TRUE
  FROM sucursal s
 WHERE NOT EXISTS (SELECT 1 FROM bodega b WHERE b.sucursal_id = s.id);

-- ── 3. bodega_id en todo lo que mueve stock ───────────────────────────────
-- Ojo: ADD COLUMN IF NOT EXISTS no detecta un tipo distinto. Si alguna de
-- estas columnas ya existiera con otro tipo, ddl-auto=validate tumba el
-- arranque y el error sale al fondo de la traza.
ALTER TABLE inventario            ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE movimiento_inventario ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE lote                  ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE serial_producto       ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE reconteos             ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE compra                ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE venta                 ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE merma                 ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE obsequio              ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE consumo_interno       ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE devolucion            ADD COLUMN IF NOT EXISTS bodega_id BIGINT;
ALTER TABLE orden_compra          ADD COLUMN IF NOT EXISTS bodega_id BIGINT;

-- El rastro del serial trasladado: sin esto, anular un traslado entre dos
-- bodegas de la MISMA sucursal no sabría a cuál devolverlo.
ALTER TABLE documento_serial ADD COLUMN IF NOT EXISTS bodega_anterior_id BIGINT;

-- El traslado ahora es entre bodegas (pueden ser de la misma sucursal).
ALTER TABLE traslado ADD COLUMN IF NOT EXISTS bodega_origen_id  BIGINT;
ALTER TABLE traslado ADD COLUMN IF NOT EXISTS bodega_destino_id BIGINT;

-- ── 4. Backfill: todo lo viejo es de la bodega principal ──────────────────
DO $$
DECLARE
    t  TEXT;
    ts TEXT[] := ARRAY['inventario', 'movimiento_inventario', 'lote', 'serial_producto',
                       'reconteos', 'compra', 'venta', 'merma', 'obsequio',
                       'consumo_interno', 'devolucion', 'orden_compra'];
BEGIN
    FOREACH t IN ARRAY ts LOOP
        EXECUTE format(
            'UPDATE %I d SET bodega_id = b.id
               FROM bodega b
              WHERE b.sucursal_id = d.sucursal_id
                AND b.es_principal
                AND d.bodega_id IS NULL', t);
    END LOOP;
END $$;

UPDATE traslado t SET bodega_origen_id = b.id
  FROM bodega b
 WHERE b.sucursal_id = t.sucursal_origen_id AND b.es_principal AND t.bodega_origen_id IS NULL;

UPDATE traslado t SET bodega_destino_id = b.id
  FROM bodega b
 WHERE b.sucursal_id = t.sucursal_destino_id AND b.es_principal AND t.bodega_destino_id IS NULL;

-- ── 5. Llaves foráneas y NOT NULL donde el dato siempre existe ────────────
-- Se hace después del backfill: antes no habría pasado la validación.
DO $$
DECLARE
    t         TEXT;
    huerfanas BOOLEAN;
    ts        TEXT[] := ARRAY['inventario', 'movimiento_inventario', 'lote', 'serial_producto',
                              'reconteos', 'compra', 'venta', 'merma', 'obsequio',
                              'consumo_interno', 'devolucion', 'orden_compra'];
BEGIN
    FOREACH t IN ARRAY ts LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS fk_%s_bodega', t, t);
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT fk_%s_bodega
                        FOREIGN KEY (bodega_id) REFERENCES bodega(id)', t, t);

        -- NOT NULL solo si el backfill no dejó huérfanas: una fila con una
        -- sucursal borrada haría fallar la migración entera.
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE bodega_id IS NULL)', t)
           INTO huerfanas;
        IF NOT huerfanas THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN bodega_id SET NOT NULL', t);
        ELSE
            RAISE NOTICE 'bodega_id queda opcional en % : hay filas sin bodega', t;
        END IF;
    END LOOP;
END $$;

ALTER TABLE traslado DROP CONSTRAINT IF EXISTS fk_traslado_bodega_origen;
ALTER TABLE traslado ADD  CONSTRAINT fk_traslado_bodega_origen
    FOREIGN KEY (bodega_origen_id) REFERENCES bodega(id);
ALTER TABLE traslado DROP CONSTRAINT IF EXISTS fk_traslado_bodega_destino;
ALTER TABLE traslado ADD  CONSTRAINT fk_traslado_bodega_destino
    FOREIGN KEY (bodega_destino_id) REFERENCES bodega(id);

-- ── 6. El saldo pasa a ser por bodega ─────────────────────────────────────
-- La unicidad vieja (sucursal_id, producto_id) impediría justamente lo que
-- viene a hacer esta migración: el mismo producto en dos bodegas del mismo
-- local. Se busca por estructura porque el nombre del índice/constraint viene
-- del esquema original de Laravel y no es el mismo en toda instalación.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname, c.contype
          FROM pg_constraint c
          JOIN pg_class t ON t.oid = c.conrelid
         WHERE t.relname = 'inventario'
           AND c.contype = 'u'
           AND (SELECT array_agg(a.attname::TEXT ORDER BY a.attname::TEXT)
                  FROM unnest(c.conkey) k
                  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k)
               = ARRAY['producto_id', 'sucursal_id']
    LOOP
        EXECUTE format('ALTER TABLE inventario DROP CONSTRAINT %I', r.conname);
    END LOOP;

    FOR r IN
        SELECT i.indexrelid::regclass AS idxname
          FROM pg_index i
          JOIN pg_class t ON t.oid = i.indrelid
         WHERE t.relname = 'inventario'
           AND i.indisunique
           AND NOT i.indisprimary
           AND (SELECT array_agg(a.attname::TEXT ORDER BY a.attname::TEXT)
                  FROM unnest(i.indkey) k
                  JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k)
               = ARRAY['producto_id', 'sucursal_id']
    LOOP
        EXECUTE format('DROP INDEX %s', r.idxname);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventario_bodega_producto
    ON inventario (bodega_id, producto_id);

-- ── 7. Índices de consulta ────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_movimiento_inventario_bodega
    ON movimiento_inventario (bodega_id, producto_id, id);
CREATE INDEX IF NOT EXISTS idx_lote_bodega    ON lote (bodega_id, producto_id);
CREATE INDEX IF NOT EXISTS idx_serial_bodega  ON serial_producto (bodega_id, producto_id);

-- ── 8. Menú ───────────────────────────────────────────────────────────────
-- El submódulo se crea aparte (docs/sql/menu_bodegas.sql) porque el menú se
-- filtra por LABEL normalizado contra un Set global: un label repetido
-- comparte visibilidad con el otro y no hay forma de separarlos por rol.
