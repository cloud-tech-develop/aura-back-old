-- ── V135: aprendiz SENA — fase y apoyo de sostenimiento (B-10) ───────────────
--
-- El aprendiz no gana salario sino apoyo de sostenimiento, y su seguridad social
-- depende de la FASE:
--   · LECTIVA  → apoyo 50% SMMLV, solo salud (EPS).
--   · PRÁCTICA → apoyo 75% SMMLV, salud + ARL.
-- (Sin pensión, parafiscales ni prestaciones en ninguna fase.)
--
-- Se agrega `fase` al contrato y los dos porcentajes al config (parametrizables /
-- versionables, como el SMMLV: si sube el desempleo la práctica puede ir a 100%).
--
-- Seguro: `fase` es nullable (los contratos no-aprendiz quedan en NULL); los
-- porcentajes traen DEFAULT, así que las filas de config existentes no se rompen.

ALTER TABLE contrato_laboral
    ADD COLUMN fase VARCHAR(20);

ALTER TABLE contrato_laboral
    ADD CONSTRAINT chk_contrato_fase CHECK (fase IS NULL OR fase IN ('LECTIVA', 'PRACTICA'));

ALTER TABLE nomina_config
    ADD COLUMN aprendiz_pct_lectiva  NUMERIC(5,2) NOT NULL DEFAULT 50;

ALTER TABLE nomina_config
    ADD COLUMN aprendiz_pct_practica NUMERIC(5,2) NOT NULL DEFAULT 75;
