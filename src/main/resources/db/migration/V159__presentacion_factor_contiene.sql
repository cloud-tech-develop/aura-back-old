-- ── V159: presentaciones — el factor pasa a ser "unidades base que contiene" ──
--
-- Hasta hoy el back dividía: factor_conversion era cuántas presentaciones caben
-- en 1 unidad base (la UNIDAD de una paca ×25 tenía factor 25 y vender 1
-- descontaba 1/25 de paca). Las pantallas enseñaban lo contrario ("Caja x 12 =
-- 12"). Desde V159 hay una sola regla: una presentación CONTIENE N unidades base
-- y la cantidad base es cantidad × factor.
--
-- Para no mover inventario, las presentaciones existentes se voltean
-- (factor := 1/factor). Vender 1 UNIDAD de la paca sigue descontando 0,04 paca,
-- igual que antes; stock, kardex e historial no se tocan. Pasar esos productos a
-- la unidad pequeña es una herramienta aparte, producto por producto.
--
-- 1. factor_conversion a NUMERIC(18,8): en la base real era (38,2) y 1/12 se
--    guardaría como 0,08. Al convertir, el back reconoce el recíproco entero.
-- 2. Volteo una sola vez: lo marca `migracion_datos_aplicada`, que comparten
--    esta migración y su espejo en Laravel. Respaldo en
--    producto_presentacion_bak_v159.
-- 3. El código de barras deja de ser único en todo el sistema: la tabla no
--    tiene empresa_id y dos droguerías no podían registrar el EAN de la misma
--    caja. La unicidad dentro de la empresa la valida el servicio.
--
-- ⚠ Desplegar junto con el back que multiplica: con el código viejo, los
--   factores volteados descontarían al revés.

-- 1. Precisión del factor
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND table_name   = 'producto_presentacion'
           AND column_name  = 'factor_conversion'
           AND (numeric_precision IS DISTINCT FROM 18 OR numeric_scale IS DISTINCT FROM 8)
    ) THEN
        ALTER TABLE producto_presentacion
            ALTER COLUMN factor_conversion TYPE NUMERIC(18,8);
    END IF;
END $$;

-- 2. Volteo del factor (una sola vez)
CREATE TABLE IF NOT EXISTS migracion_datos_aplicada (
    clave        VARCHAR(80) PRIMARY KEY,
    aplicada_en  TIMESTAMP   NOT NULL DEFAULT now(),
    detalle      TEXT
);

COMMENT ON TABLE migracion_datos_aplicada IS
    'Migraciones de DATOS no repetibles (voltear, recalcular). La comparten Flyway y Laravel para que ninguna corra dos veces.';

DO $$
DECLARE
    v_filas INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM migracion_datos_aplicada WHERE clave = 'V159_factor_contiene') THEN
        CREATE TABLE IF NOT EXISTS producto_presentacion_bak_v159 AS
            SELECT id, producto_id, nombre, factor_conversion, now() AS respaldado_en
              FROM producto_presentacion;

        UPDATE producto_presentacion
           SET factor_conversion = ROUND(1.0 / factor_conversion, 8)
         WHERE factor_conversion > 0
           AND factor_conversion <> 1;

        GET DIAGNOSTICS v_filas = ROW_COUNT;

        INSERT INTO migracion_datos_aplicada (clave, detalle)
        VALUES ('V159_factor_contiene', v_filas || ' presentaciones volteadas (factor := 1/factor)');
    END IF;
END $$;

COMMENT ON COLUMN producto_presentacion.factor_conversion IS
    'Unidades base que CONTIENE la presentación (Caja x10 = 10). Cantidad base = cantidad x factor. Menor que 1 = presentación más pequeña que la base.';

-- 3. Código de barras: sin unicidad global (el nombre de la restricción cambia entre bases)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'producto_presentacion'::regclass
           AND contype  = 'u'
           AND pg_get_constraintdef(oid) = 'UNIQUE (codigo_barras)'
    LOOP
        EXECUTE format('ALTER TABLE producto_presentacion DROP CONSTRAINT %I', r.conname);
    END LOOP;

    FOR r IN
        SELECT i.relname AS indice
          FROM pg_index x
          JOIN pg_class i ON i.oid = x.indexrelid
         WHERE x.indrelid = 'producto_presentacion'::regclass
           AND x.indisunique
           AND NOT x.indisprimary
           AND pg_get_indexdef(x.indexrelid) ~ '\(codigo_barras\)$'
    LOOP
        EXECUTE format('DROP INDEX %I', r.indice);
    END LOOP;
END $$;

CREATE INDEX IF NOT EXISTS idx_producto_presentacion_codigo_barras
    ON producto_presentacion (codigo_barras);
