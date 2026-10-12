-- V224 — Nómina electrónica MENSUAL: la DIAN recibe un solo documento por empleado y
-- mes con todo lo pagado (las dos quincenas, prima, vacaciones…). El documento deja de
-- ser "de una nómina" y pasa a ser "del contrato en el mes": se agrega contrato_id
-- (nomina_id queda apuntando a la última nómina del mes, la que lo completó).
-- Las filas existentes toman el contrato de su nómina.

ALTER TABLE nomina_electronica ADD COLUMN IF NOT EXISTS contrato_id BIGINT;

UPDATE nomina_electronica ne
   SET contrato_id = n.contrato_id
  FROM nomina n
 WHERE n.id = ne.nomina_id
   AND ne.contrato_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_ne_contrato_mes
    ON nomina_electronica (empresa_id, contrato_id, agno, mes);
