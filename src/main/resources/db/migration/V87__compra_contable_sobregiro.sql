-- E2 · Piezas 4 y 5 de tesorería.
-- Compra con destino contable: el débito puede ir a gasto (5195 papelería),
-- activo (15xx) u otra cuenta en vez de inventario, con centro de costo
-- propagado a todas las líneas del asiento.
ALTER TABLE compra
    ADD COLUMN IF NOT EXISTS centro_costo_id BIGINT,
    ADD COLUMN IF NOT EXISTS cuenta_contable_id BIGINT;

-- Sobregiro bancario (enfoque A): el banco puede quedar en negativo hasta el
-- cupo; en el cierre de período el saldo crédito se reclasifica a 21xx.
ALTER TABLE cuenta_bancaria
    ADD COLUMN IF NOT EXISTS permite_sobregiro BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cupo_sobregiro NUMERIC(18,2);
