-- ── V74: Nómina — origen del pago (medio y cuenta bancaria) ──────────────────

ALTER TABLE nomina
    ADD COLUMN medio_pago         VARCHAR(20),   -- EFECTIVO | TRANSFERENCIA
    ADD COLUMN cuenta_bancaria_id BIGINT REFERENCES cuenta_bancaria(id),
    ADD COLUMN fecha_pago         TIMESTAMP;
