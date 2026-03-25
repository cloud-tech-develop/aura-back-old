-- Guardar el monto tendido por el cliente (para cálculo de cambio en tirilla)
-- monto     = monto real aplicado a la venta (lo que cuenta en el total)
-- monto_recibido = monto que entregó el cliente (puede ser mayor en EFECTIVO)
ALTER TABLE venta_pago
    ADD COLUMN IF NOT EXISTS monto_recibido NUMERIC(15, 2);
