-- V206 — cierre de mes, fases 2 a 5 (docs/PLAN_CIERRE_CONTABLE.md).
--
-- Al cerrar un mes se guarda la lista de chequeo tal como estaba (JSON): es lo
-- que imprime el acta de cierre. Recalcularla después daría otro resultado,
-- porque los meses siguientes mueven la cartera, el inventario y los bancos.

ALTER TABLE periodo_contable ADD COLUMN IF NOT EXISTS chequeo_cierre TEXT;
