-- ── V144: reactivar una cotización vencida ──────────────────────────────────
--
-- Las cotizaciones vencen a los `dias_vigencia` y el scheduler las pasa a
-- VENCIDA. Desde ahí no se pueden convertir a venta, así que cuando el cliente
-- vuelve tarde el vendedor tiene que pedir que se la reactiven a mano.
--
-- Con esto la reactiva el propio usuario, una sola vez y conservando los
-- precios originales: si el cliente vuelve una segunda vez, alguien del negocio
-- tiene que mirar de nuevo el precio antes de comprometerlo otra vez.
--
-- Se guarda quién y cuándo porque revivir un precio viejo es una decisión
-- comercial y tiene que quedar rastro de quién la tomó.

ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS veces_reactivada INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS reactivada_at    TIMESTAMP;
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS reactivada_por   INTEGER;

-- Las cotizaciones existentes nunca se han reactivado, que es el DEFAULT.
