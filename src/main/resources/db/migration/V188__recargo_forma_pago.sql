-- V188 — recargo por forma de pago.
--
-- Algunas formas de pago (Sistecrédito, ADDI…) se cobran con un recargo que
-- paga el cliente: al vender con ellas el total sube ese porcentaje sobre lo
-- que se paga por esa forma. El recargo entra a la venta como una línea más
-- (producto de servicio "Recargo por forma de pago", SKU RECARGO-FP, que el
-- sistema crea solo la primera vez), así queda en la factura, en el total y
-- en el ingreso contable.

ALTER TABLE forma_pago_contable
    ADD COLUMN IF NOT EXISTS recargo_porcentaje NUMERIC(7,4) NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_forma_pago_recargo') THEN
        ALTER TABLE forma_pago_contable
            ADD CONSTRAINT ck_forma_pago_recargo CHECK (recargo_porcentaje >= 0 AND recargo_porcentaje <= 100);
    END IF;
END $$;
