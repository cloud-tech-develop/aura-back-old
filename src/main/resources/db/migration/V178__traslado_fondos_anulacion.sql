-- V178 — Anulación del traslado de fondos.
--
-- El estado ANULADO existía desde V147 pero no había cómo llegar a él: un
-- traslado equivocado solo se corregía con una nota contable, y el movimiento
-- del arqueo y del extracto bancario quedaba vivo. Ahora se anula con motivo
-- (nunca se borra) y el servicio devuelve la plata y reversa el asiento.

ALTER TABLE traslado_fondos ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(500);
ALTER TABLE traslado_fondos ADD COLUMN IF NOT EXISTS anulado_por      INT;
ALTER TABLE traslado_fondos ADD COLUMN IF NOT EXISTS anulado_at       TIMESTAMP;

-- La relación de la caja menor busca las reposiciones de una cuenta.
CREATE INDEX IF NOT EXISTS idx_traslado_fondos_destino_cuenta
    ON traslado_fondos (empresa_id, destino_cuenta_id)
    WHERE destino_cuenta_id IS NOT NULL;
