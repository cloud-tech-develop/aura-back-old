-- ── V158: uso del producto — VENTA / INSUMO / AMBOS ─────────────────────────
--
-- Hasta hoy "insumo" se simulaba apagando `visible_en_pos`. Eso esconde el
-- producto del POS, pero no dice nada de para qué sirve: el selector de
-- componentes de una receta no podía distinguir la harina de la gaseosa.
--
-- Va en columna aparte de `tipo_producto` porque son ejes distintos:
-- `tipo_producto` dice cómo se vende o se mide (estándar, kit, pesable,
-- servicio); `uso_producto` dice para qué está en el catálogo. La harina es
-- PESABLE e INSUMO a la vez.
--
--   VENTA   se vende en el POS
--   INSUMO  entra en recetas; nunca aparece en el POS
--   AMBOS   se vende suelto y también es componente (la gaseosa de un combo)
--
-- Backfill (solo cuando la columna se crea, para no pisar lo que el usuario
-- ya haya clasificado):
--   oculto del POS                         → INSUMO
--   visible y componente de alguna receta  → AMBOS
--   el resto                               → VENTA

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND table_name   = 'producto'
           AND column_name  = 'uso_producto'
    ) THEN
        ALTER TABLE producto
            ADD COLUMN uso_producto VARCHAR(10) NOT NULL DEFAULT 'VENTA';

        UPDATE producto
           SET uso_producto = 'INSUMO'
         WHERE visible_en_pos = false;

        UPDATE producto p
           SET uso_producto = 'AMBOS'
         WHERE p.uso_producto = 'VENTA'
           AND EXISTS (SELECT 1 FROM producto_composicion pc
                        WHERE pc.producto_hijo_id = p.id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_producto_uso') THEN
        ALTER TABLE producto
            ADD CONSTRAINT chk_producto_uso CHECK (uso_producto IN ('VENTA', 'INSUMO', 'AMBOS'));
    END IF;
END $$;

COMMENT ON COLUMN producto.uso_producto IS
    'VENTA | INSUMO | AMBOS. Un INSUMO no se muestra en el POS y es el que ofrece el selector de componentes de receta.';

CREATE INDEX IF NOT EXISTS idx_producto_empresa_uso ON producto (empresa_id, uso_producto);
