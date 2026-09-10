-- =====================================================================
--  SINCRONIZAR SECUENCIAS DE IDs  (PostgreSQL)
--
--  Problema que resuelve:
--    Cuando los datos entran con el id explicito (restauracion de un
--    dump, carga masiva, migracion desde otro proyecto), la secuencia
--    del id NO avanza. Despues, al crear un producto / cliente / lo que
--    sea desde la aplicacion, Postgres pide el siguiente valor de la
--    secuencia, ese valor ya existe y falla con:
--        duplicate key value violates unique constraint "xxx_pkey"
--
--  Que hace:
--    Recorre TODAS las tablas del esquema public que tienen una columna
--    serial o identity y adelanta su secuencia hasta el MAX(id) real.
--    No es por empresa: la secuencia es de la tabla, no del cliente.
--
--  Seguridad:
--    - No modifica ni una sola fila de datos.
--    - Solo ADELANTA secuencias, nunca las retrocede.
--    - Es idempotente: correrlo dos veces no hace nada la segunda vez.
--    - Correr como dueno de la base (o con permiso sobre las secuencias).
-- =====================================================================

-- ---------------------------------------------------------------------
--  PASO 1. DIAGNOSTICO - que secuencias estan desincronizadas
--  (solo lectura, no cambia nada; se puede correr en produccion)
-- ---------------------------------------------------------------------
WITH cols AS (
    SELECT (quote_ident(n.nspname) || '.' || quote_ident(c.relname))          AS tabla,
           a.attname                                                          AS columna,
           pg_get_serial_sequence(quote_ident(n.nspname) || '.' ||
                                  quote_ident(c.relname), a.attname)          AS secuencia
      FROM pg_class      c
      JOIN pg_namespace  n ON n.oid = c.relnamespace
      JOIN pg_attribute  a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
     WHERE c.relkind = 'r'
       AND n.nspname = 'public'
       AND (a.attidentity IN ('a','d')
            OR EXISTS (SELECT 1 FROM pg_attrdef d
                        WHERE d.adrelid = c.oid AND d.adnum = a.attnum
                          AND pg_get_expr(d.adbin, d.adrelid) LIKE 'nextval%'))
),
datos AS (
    SELECT cols.*,
           COALESCE((xpath('/row/m/text()',
                query_to_xml(format('SELECT MAX(%I) AS m FROM %s', columna, tabla),
                             false, true, '')))[1]::text::bigint, 0)          AS max_id,
           (SELECT s.last_value FROM pg_sequences s
             WHERE quote_ident(s.schemaname) || '.' || quote_ident(s.sequencename) = cols.secuencia) AS valor_secuencia
      FROM cols
     WHERE cols.secuencia IS NOT NULL
)
SELECT tabla, columna, max_id, valor_secuencia,
       CASE WHEN valor_secuencia IS NULL      THEN 'SIN USAR - se corrige'
            WHEN max_id > valor_secuencia     THEN 'DESINCRONIZADA - se corrige'
            ELSE 'ok' END AS estado
  FROM datos
 WHERE valor_secuencia IS NULL OR max_id >= valor_secuencia
 ORDER BY (max_id - COALESCE(valor_secuencia, 0)) DESC, tabla;

-- ---------------------------------------------------------------------
--  PASO 2. CORRECCION - adelanta las secuencias atrasadas
-- ---------------------------------------------------------------------
DO $$
DECLARE
    r            record;
    v_max        bigint;
    v_last       bigint;
    v_is_called  boolean;
    v_corregidas int := 0;
    v_revisadas  int := 0;
BEGIN
    FOR r IN
        SELECT (quote_ident(n.nspname) || '.' || quote_ident(c.relname))       AS tabla,
               a.attname                                                       AS columna,
               pg_get_serial_sequence(quote_ident(n.nspname) || '.' ||
                                      quote_ident(c.relname), a.attname)       AS secuencia
          FROM pg_class      c
          JOIN pg_namespace  n ON n.oid = c.relnamespace
          JOIN pg_attribute  a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
         WHERE c.relkind = 'r'
           AND n.nspname = 'public'
           AND (a.attidentity IN ('a','d')
                OR EXISTS (SELECT 1 FROM pg_attrdef d
                            WHERE d.adrelid = c.oid AND d.adnum = a.attnum
                              AND pg_get_expr(d.adbin, d.adrelid) LIKE 'nextval%'))
         ORDER BY 1
    LOOP
        CONTINUE WHEN r.secuencia IS NULL;
        v_revisadas := v_revisadas + 1;

        EXECUTE format('SELECT COALESCE(MAX(%I), 0) FROM %s', r.columna, r.tabla)
           INTO v_max;
        EXECUTE format('SELECT last_value, is_called FROM %s', r.secuencia)
           INTO v_last, v_is_called;

        -- solo hacia adelante: si la secuencia ya va mas alta, no se toca
        IF (v_is_called AND v_max > v_last) OR (NOT v_is_called AND v_max >= v_last) THEN
            PERFORM setval(r.secuencia, v_max, true);
            v_corregidas := v_corregidas + 1;
            RAISE NOTICE 'corregida %  (%.% : % -> %)',
                         r.secuencia, r.tabla, r.columna, v_last, v_max;
        END IF;
    END LOOP;

    RAISE NOTICE '--------------------------------------------------';
    RAISE NOTICE 'secuencias revisadas: %   corregidas: %', v_revisadas, v_corregidas;
END $$;

-- ---------------------------------------------------------------------
--  PASO 3. VERIFICACION - debe devolver CERO filas
-- ---------------------------------------------------------------------
WITH cols AS (
    SELECT (quote_ident(n.nspname) || '.' || quote_ident(c.relname))          AS tabla,
           a.attname                                                          AS columna,
           pg_get_serial_sequence(quote_ident(n.nspname) || '.' ||
                                  quote_ident(c.relname), a.attname)          AS secuencia
      FROM pg_class      c
      JOIN pg_namespace  n ON n.oid = c.relnamespace
      JOIN pg_attribute  a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
     WHERE c.relkind = 'r'
       AND n.nspname = 'public'
       AND (a.attidentity IN ('a','d')
            OR EXISTS (SELECT 1 FROM pg_attrdef d
                        WHERE d.adrelid = c.oid AND d.adnum = a.attnum
                          AND pg_get_expr(d.adbin, d.adrelid) LIKE 'nextval%'))
)
SELECT tabla, columna,
       COALESCE((xpath('/row/m/text()',
            query_to_xml(format('SELECT MAX(%I) AS m FROM %s', columna, tabla),
                         false, true, '')))[1]::text::bigint, 0)              AS max_id,
       (SELECT s.last_value FROM pg_sequences s
         WHERE quote_ident(s.schemaname) || '.' || quote_ident(s.sequencename) = cols.secuencia) AS valor_secuencia
  FROM cols
 WHERE cols.secuencia IS NOT NULL
   AND COALESCE((xpath('/row/m/text()',
            query_to_xml(format('SELECT MAX(%I) AS m FROM %s', columna, tabla),
                         false, true, '')))[1]::text::bigint, 0)
       > COALESCE((SELECT s.last_value FROM pg_sequences s
                    WHERE quote_ident(s.schemaname) || '.' || quote_ident(s.sequencename) = cols.secuencia), 0);
