-- Tesorería contable (Pieza C):
-- Cada movimiento de tesorería (recaudo/egreso/transferencia) puede llevar una
-- cuenta contable de contrapartida. El lado del banco lo resuelve la cuenta
-- bancaria (cuenta_contable_id); esta columna es el otro lado del asiento.
ALTER TABLE tesoreria_movimiento
    ADD COLUMN IF NOT EXISTS contrapartida_cuenta_id BIGINT NULL;
