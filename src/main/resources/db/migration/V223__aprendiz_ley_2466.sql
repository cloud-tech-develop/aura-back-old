-- V223 — Aprendiz SENA según la Ley 2466 de 2025 (Decreto 223 de 2026): el apoyo de
-- sostenimiento pasa a 75 % del SMMLV en etapa lectiva y 100 % en etapa práctica
-- (antes 50 % y 75 %, Ley 789 de 2002). Solo se suben las empresas que seguían con
-- los valores por defecto anteriores; una empresa que ya los había cambiado a mano
-- se respeta.

ALTER TABLE nomina_config ALTER COLUMN aprendiz_pct_lectiva SET DEFAULT 75;

ALTER TABLE nomina_config ALTER COLUMN aprendiz_pct_practica SET DEFAULT 100;

UPDATE nomina_config SET aprendiz_pct_lectiva = 75 WHERE aprendiz_pct_lectiva = 50;

UPDATE nomina_config SET aprendiz_pct_practica = 100 WHERE aprendiz_pct_practica = 75;
