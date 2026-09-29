-- V168 — promesas de pago con seguimiento.
-- 
-- Una gestión con resultado PROMESA_PAGO queda PENDIENTE y el sistema la resuelve:
-- CUMPLIDA si entran abonos del cliente por el monto prometido entre la gestión y la
-- fecha prometida; INCUMPLIDA si pasa la fecha sin completarse; CANCELADA si una
-- promesa nueva la reemplaza. Las promesas viejas con fecha y monto se toman como
-- PENDIENTE para que la primera evaluación las resuelva.

ALTER TABLE gestion_cobro ADD COLUMN IF NOT EXISTS estado_promesa VARCHAR(12);

ALTER TABLE gestion_cobro ADD COLUMN IF NOT EXISTS monto_pagado_promesa NUMERIC(15,2);

ALTER TABLE gestion_cobro ADD COLUMN IF NOT EXISTS promesa_resuelta_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_gestion_cobro_estado_promesa') THEN
        ALTER TABLE gestion_cobro ADD CONSTRAINT ck_gestion_cobro_estado_promesa
            CHECK (estado_promesa IS NULL OR estado_promesa IN ('PENDIENTE','CUMPLIDA','INCUMPLIDA','CANCELADA'));
    END IF;
END $$;

UPDATE gestion_cobro SET estado_promesa = 'PENDIENTE'
WHERE estado_promesa IS NULL AND resultado = 'PROMESA_PAGO'
  AND fecha_promesa_pago IS NOT NULL AND COALESCE(monto_prometido, 0) > 0;

CREATE INDEX IF NOT EXISTS idx_gestion_cobro_promesa
    ON gestion_cobro (empresa_id, estado_promesa, fecha_promesa_pago);

CREATE INDEX IF NOT EXISTS idx_gestion_cobro_tercero_fecha
    ON gestion_cobro (empresa_id, tercero_id, created_at DESC);
