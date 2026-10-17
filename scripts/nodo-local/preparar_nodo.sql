-- ─────────────────────────────────────────────────────────────────────────────
-- Prepara la base de un SERVIDOR LOCAL (modo híbrido, docs/MODO_HIBRIDO.md).
--
-- Se corre UNA vez, en la base del nodo, después de crear el esquema y ANTES
-- de la primera sincronización. NUNCA en la base de la nube.
--
-- Mueve todas las secuencias a 1.500.000.000 (SyncIds.BASE_LOCAL). Lo que baja
-- de la nube conserva su id (siempre menor); lo que se crea en la tienda queda
-- por encima. Así nunca chocan y la nube sabe qué ids debe traducir.
--
--   psql -U postgres -d aura_nodo -f scripts/nodo-local/preparar_nodo.sql
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    base  CONSTANT bigint := 1500000000;
    s     record;
    ultimo bigint;
BEGIN
    -- Protección: una base con ventas es la nube (o un nodo ya en uso).
    IF EXISTS (SELECT 1 FROM venta WHERE id < base) THEN
        RAISE EXCEPTION 'Esta base ya tiene ventas con ids de la nube: no parece la base de un nodo nuevo';
    END IF;

    FOR s IN
        SELECT schemaname, sequencename, max_value
        FROM pg_sequences
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('SELECT last_value FROM %I.%I', s.schemaname, s.sequencename) INTO ultimo;
        IF ultimo < base AND s.max_value > base THEN
            EXECUTE format('ALTER SEQUENCE %I.%I RESTART WITH %s', s.schemaname, s.sequencename, base);
            RAISE NOTICE 'Secuencia % movida a %', s.sequencename, base;
        END IF;
    END LOOP;
END
$$;
