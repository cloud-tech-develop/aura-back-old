-- V222 — Nómina electrónica Factus v2: las notas de ajuste (eliminación) se guardan
-- como filas propias (es_ajuste = TRUE, nomina_ajustada_id = la original) con el número
-- que les asigna Factus. El número es prefijo + consecutivo y cada rango de Factus tiene
-- su prefijo, así que el consecutivo solo es único dentro de su prefijo: la regla vieja
-- (empresa, año, consecutivo) chocaba entre la nómina NEF5 y la nota NAJ5.

ALTER TABLE nomina_electronica DROP CONSTRAINT IF EXISTS uq_ne_consecutivo;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ne_prefijo_consecutivo
    ON nomina_electronica (empresa_id, agno, COALESCE(prefijo, ''), consecutivo);
