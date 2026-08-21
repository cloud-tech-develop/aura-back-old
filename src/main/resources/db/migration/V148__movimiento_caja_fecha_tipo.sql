-- ── V148: corrige el tipo de movimiento_caja.fecha ─────────────────────────
--
-- La V145 daba por hecho que la columna `fecha` no existía y la agregaba con
-- ADD COLUMN IF NOT EXISTS. Pero la columna existe desde que se creó la tabla,
-- como TIMESTAMP con DEFAULT now(): nunca estuvo mapeada en la entidad, así que
-- en la práctica guardaba lo mismo que `created_at`. El IF NOT EXISTS no hizo
-- nada y la columna se quedó en timestamp, mientras la entidad la declara
-- LocalDate. Con ddl-auto=validate la aplicación no arranca:
--
--   Schema-validation: wrong column type encountered in column [fecha] in table
--   [movimiento_caja]; found [timestamp], but expecting [date]
--
-- Esta migración hace la conversión que faltaba. La V145 ya quedó corregida
-- para instalaciones nuevas; esta existe para las bases donde la V145 vieja ya
-- se aplicó. En una base ya correcta no hace nada.
--
-- No se pierde información: el instante exacto sigue en `created_at`, que es la
-- columna con la que el código ordena los movimientos del turno.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'movimiento_caja'
           AND column_name = 'fecha'
           AND data_type <> 'date'
    ) THEN
        -- El DEFAULT se quita antes de cambiar el tipo: Postgres no puede
        -- convertir un default de timestamp a una columna date.
        ALTER TABLE movimiento_caja ALTER COLUMN fecha DROP DEFAULT;
        ALTER TABLE movimiento_caja ALTER COLUMN fecha TYPE DATE USING fecha::date;
        ALTER TABLE movimiento_caja ALTER COLUMN fecha SET DEFAULT CURRENT_DATE;
    END IF;
END $$;

UPDATE movimiento_caja SET fecha = created_at::date WHERE fecha IS NULL;

ALTER TABLE movimiento_caja ALTER COLUMN fecha SET NOT NULL;
