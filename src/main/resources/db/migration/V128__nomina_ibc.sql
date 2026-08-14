-- ── V128: guardar el IBC (base de cotización) en la nómina ───────────────────
--
-- El motor de liquidación ya calcula la base de cotización correcta
-- (BasesLiquidacion.baseIbc = sin auxilio de transporte ni pagos no salariales),
-- pero no se persistía. PILA caía en usar `total_devengado` como IBC, que INFLA
-- el IBC con rubros no constitutivos → IBC × tarifa ≠ aporte (inexactitud UGPP).
--
-- Nullable: las nóminas ya liquidadas quedan en NULL hasta reliquidar; PILA usa
-- total_devengado como respaldo cuando ibc es NULL.

ALTER TABLE nomina
    ADD COLUMN IF NOT EXISTS ibc NUMERIC(15,2);

COMMENT ON COLUMN nomina.ibc IS
    'Base de cotización (IBC) sobre la que se liquidaron los aportes. Excluye '
    'auxilio de transporte y pagos no salariales. La usa PILA como IBC.';
