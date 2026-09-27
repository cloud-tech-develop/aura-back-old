-- V169 — control de crédito completo.
-- 
-- Reglas de crédito con descripción y días entre aplicaciones (para que un aumento
-- de cupo no se acumule en cada pago); el historial guarda qué regla actuó.
-- Solicitudes de autorización de cupo con quién la pidió, vigencia de la
-- aprobación y cuándo la consumió una venta (estados USADA y VENCIDA).

ALTER TABLE regla_credito ADD COLUMN IF NOT EXISTS descripcion VARCHAR(300);

ALTER TABLE regla_credito ADD COLUMN IF NOT EXISTS dias_entre_aplicaciones INTEGER NOT NULL DEFAULT 30;

ALTER TABLE regla_credito ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE historial_credito ADD COLUMN IF NOT EXISTS regla_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_historial_credito_regla
    ON historial_credito (regla_id, tercero_id, created_at DESC);

ALTER TABLE solicitud_autorizacion_credito ADD COLUMN IF NOT EXISTS solicitado_por_id INTEGER;

ALTER TABLE solicitud_autorizacion_credito ADD COLUMN IF NOT EXISTS observacion VARCHAR(300);

ALTER TABLE solicitud_autorizacion_credito ADD COLUMN IF NOT EXISTS respondido_at TIMESTAMP;

ALTER TABLE solicitud_autorizacion_credito ADD COLUMN IF NOT EXISTS vigente_hasta TIMESTAMP;

ALTER TABLE solicitud_autorizacion_credito ADD COLUMN IF NOT EXISTS usada_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_solicitud_credito_estado') THEN
        ALTER TABLE solicitud_autorizacion_credito ADD CONSTRAINT ck_solicitud_credito_estado
            CHECK (estado IN ('PENDIENTE','APROBADA','RECHAZADA','USADA','VENCIDA'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_solicitud_credito_estado
    ON solicitud_autorizacion_credito (empresa_id, estado, created_at DESC);
