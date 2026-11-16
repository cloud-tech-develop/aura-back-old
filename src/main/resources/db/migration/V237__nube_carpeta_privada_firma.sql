-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  V237 — Carpeta privada de UNA firma (docs/PLAN_NUBE_DOCUMENTOS.md)  ║
-- ║                                                                      ║
-- ║  Antes SOLO_FIRMA = "cualquier firma": si el cliente cambiaba de     ║
-- ║  contador, la firma nueva veía los papeles de la anterior. Ahora la  ║
-- ║  carpeta privada guarda de qué firma es y solo esa la ve. Cuando el  ║
-- ║  vínculo termina, la tarea programada la vuelve COMPARTIDA (los      ║
-- ║  documentos son del cliente). Idempotente.                           ║
-- ╚════════════════════════════════════════════════════════════════════╝

ALTER TABLE nube_carpeta ADD COLUMN IF NOT EXISTS privada_firma_id INT;

-- Lo que ya existía: la firma que creó la carpeta.
UPDATE nube_carpeta SET privada_firma_id = creado_por_firma_id
 WHERE visibilidad = 'SOLO_FIRMA' AND privada_firma_id IS NULL;

-- Privadas sin firma conocida: pasan al cliente.
UPDATE nube_carpeta SET visibilidad = 'COMPARTIDA'
 WHERE visibilidad = 'SOLO_FIRMA' AND privada_firma_id IS NULL;

UPDATE nube_carpeta SET privada_firma_id = NULL
 WHERE visibilidad = 'COMPARTIDA' AND privada_firma_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_nube_carpeta_privada_tiene_firma') THEN
        ALTER TABLE nube_carpeta
            ADD CONSTRAINT ck_nube_carpeta_privada_tiene_firma
            CHECK ((visibilidad = 'SOLO_FIRMA') = (privada_firma_id IS NOT NULL));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_nube_carpeta_privada
    ON nube_carpeta (empresa_id, privada_firma_id) WHERE privada_firma_id IS NOT NULL;
