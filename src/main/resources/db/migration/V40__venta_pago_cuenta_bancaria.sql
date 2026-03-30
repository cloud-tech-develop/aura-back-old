ALTER TABLE venta_pago
    ADD COLUMN IF NOT EXISTS cuenta_bancaria_id BIGINT NULL,
    ADD CONSTRAINT fk_venta_pago_cuenta_bancaria
        FOREIGN KEY (cuenta_bancaria_id) REFERENCES cuenta_bancaria(id);
