-- ── Alinear venta.fecha_emision en una base de DESARROLLO ─────────────────
--
-- No es una migración: en producción `fecha_emision` YA es `timestamp` sin zona
-- (verificado contra la base real el 2026-08-24), así que allí no hay nada que
-- hacer. Esto existe solo para las bases locales que quedaron con la columna
-- CON zona, y que por eso se comportan distinto que producción.
--
-- Por qué importa alinearlas: una columna CON zona deja que el driver JDBC
-- interprete el LocalDateTime usando la zona de la JVM, así que el instante
-- guardado cambia según dónde corra el servidor. Una columna SIN zona guarda
-- literalmente lo que Java entrega. Depurar un problema de horas contra una
-- base que difiere de producción en esto lleva a conclusiones que no aplican.
--
-- ANTES DE CORRERLO: confirma en qué zona corría la JVM cuando se guardaron
-- esas ventas, con la consulta 6 de diagnostico_desfase_horario.sql:
--
--     dif_bogota_h ≈ 0   → usa 'America/Bogota'  (el caso de la base local)
--     dif_utc_h    ≈ -5  → usa 'UTC'
--
-- Elegir mal corre la hora de todas las ventas de esa base.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'venta'
          AND column_name = 'fecha_emision'
          AND data_type = 'timestamp with time zone'
    ) THEN
        ALTER TABLE venta
            ALTER COLUMN fecha_emision TYPE TIMESTAMP
            USING fecha_emision AT TIME ZONE 'America/Bogota';
        RAISE NOTICE 'venta.fecha_emision convertida a timestamp sin zona';
    ELSE
        RAISE NOTICE 'venta.fecha_emision ya es timestamp sin zona: nada que hacer';
    END IF;
END $$;
