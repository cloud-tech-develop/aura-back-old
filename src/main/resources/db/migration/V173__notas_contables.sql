-- V173 — Notas contables (comprobante de diario CD) con ciclo borrador.
--
-- La nota de diario la elabora el contador: provisiones, reclasificaciones,
-- correcciones. Antes se contabilizaba al guardar y la única corrección era
-- anular y volver a crear, así que no se podía armar una nota, revisarla y
-- aprobarla después, ni saber quién la aprobó. Desde aquí la nota nace en
-- BORRADOR sin consecutivo (editable y borrable), el CD-###### se asigna al
-- contabilizar, y la anulación exige motivo y deja traza.
--
-- No hay tabla nueva: la nota sigue siendo un asiento_contable MANUAL con
-- tipo_comprobante = 'CD'. Solo se agregan los campos de auditoría.

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS contabilizado_por INTEGER;

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS contabilizado_at TIMESTAMP;

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS anulado_por INTEGER;

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS anulado_at TIMESTAMP;

ALTER TABLE asiento_contable ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(300);

-- Los asientos manuales creados antes de V173 como nota de diario (numerados
-- CD-) no guardaban el tipo: se completa para que el listado los reconozca.
UPDATE asiento_contable
   SET tipo_comprobante = 'CD'
 WHERE tipo_origen = 'MANUAL'
   AND tipo_comprobante IS NULL
   AND numero_comprobante LIKE 'CD-%';

CREATE INDEX IF NOT EXISTS ix_asiento_notas_diario
    ON asiento_contable (empresa_id, estado, fecha)
    WHERE tipo_origen = 'MANUAL' AND tipo_comprobante = 'CD';
