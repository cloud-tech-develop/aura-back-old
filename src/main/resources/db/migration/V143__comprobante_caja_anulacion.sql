-- ── V143: anulación de comprobantes de caja ─────────────────────────────────
--
-- Un comprobante emitido no se puede borrar: su consecutivo ya circuló y
-- eliminarlo dejaría un hueco en la serie CE/RC que un auditor no puede
-- explicar. Pero sí hace falta poder invalidarlo, porque hoy queda afirmando un
-- pago que ya no existe en dos casos reales:
--
--   1. La compra se registró de CONTADO y al editarla se corrige a CRÉDITO
--      (equivocarse de medio de pago es lo más común). El egreso ya no ocurrió:
--      la plata sale después, con el abono a la cuenta por pagar.
--   2. La compra se anula por completo.
--
-- Con estas columnas el comprobante sobrevive con su número, marcado como
-- anulado y con el motivo, que es como se maneja un soporte en contabilidad.

ALTER TABLE comprobante_caja ADD COLUMN IF NOT EXISTS anulado          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE comprobante_caja ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(300);
ALTER TABLE comprobante_caja ADD COLUMN IF NOT EXISTS anulado_at       TIMESTAMP;

-- Los comprobantes existentes están todos vigentes, que es el DEFAULT.
