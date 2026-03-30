-- V42: Método de pago en liquidación de comisiones

ALTER TABLE comision_liquidacion
    ADD COLUMN IF NOT EXISTS metodo_pago       VARCHAR(30)  NULL,
    ADD COLUMN IF NOT EXISTS cuenta_bancaria_id BIGINT       NULL REFERENCES cuenta_bancaria(id);
