-- V39 — Agregar cuenta_bancaria_id a compra_pago
ALTER TABLE compra_pago
    ADD COLUMN IF NOT EXISTS cuenta_bancaria_id BIGINT REFERENCES cuenta_bancaria(id);

CREATE INDEX IF NOT EXISTS idx_compra_pago_cuenta ON compra_pago(cuenta_bancaria_id);
