-- F3 — Vacaciones con saldo.
--
--   nomina_config.permite_vacaciones_anticipadas → si FALSE (default), no se
--       pueden tomar más días de los causados; si TRUE, solo advierte.
--   empleados.vacaciones_saldo_inicial → días de vacaciones que el empleado trae
--       de su sistema anterior (las empresas migran). Suma al saldo causado.

ALTER TABLE nomina_config
    ADD COLUMN IF NOT EXISTS permite_vacaciones_anticipadas BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE empleados
    ADD COLUMN IF NOT EXISTS vacaciones_saldo_inicial NUMERIC(6,2) NOT NULL DEFAULT 0;
