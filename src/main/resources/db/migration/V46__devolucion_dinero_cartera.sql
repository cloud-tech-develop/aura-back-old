-- V46: Devolución de dinero y afectación de cartera
ALTER TABLE devolucion
    ADD COLUMN IF NOT EXISTS metodo_devolucion       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS afecto_cartera          BOOLEAN       DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS monto_cartera_afectado  NUMERIC(18,2);

COMMENT ON COLUMN devolucion.metodo_devolucion       IS 'EFECTIVO | TRANSFERENCIA | NOTA_CREDITO | SIN_DEVOLUCION';
COMMENT ON COLUMN devolucion.afecto_cartera          IS 'TRUE si se redujo saldo en cuentas_cobrar';
COMMENT ON COLUMN devolucion.monto_cartera_afectado  IS 'Monto efectivamente descontado de cuentas_cobrar';
