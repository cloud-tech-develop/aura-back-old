-- V216 — Liquidación de la prenómina por documento (rediseño R3.2c).
--
-- nomina.liquidada_at: cuándo el motor liquidó la fila por última vez. NULL = no
--   liquidada (recién agregada o el último intento falló).
-- nomina.error_liquidacion: causa del último intento fallido (sin salario
--   vigente, asistencia sin aprobar…). La pestaña Liquidación la muestra como
--   "no liquidado"; la confirmación (R4) exigirá que no quede ninguna.
-- proceso_nomina: nuevo tipo LIQUIDACION_DOCUMENTO (liquidación en segundo plano
--   de las filas de un documento; referencia_id = nomina_documento.id).

ALTER TABLE nomina ADD COLUMN IF NOT EXISTS liquidada_at TIMESTAMP;

ALTER TABLE nomina ADD COLUMN IF NOT EXISTS error_liquidacion VARCHAR(500);

ALTER TABLE proceso_nomina DROP CONSTRAINT IF EXISTS chk_proc_tipo;

ALTER TABLE proceso_nomina ADD CONSTRAINT chk_proc_tipo CHECK (tipo IN ('LIQUIDACION_PERIODO',
    'LIQUIDACION_PRESTACIONES', 'NOMINA_ELECTRONICA', 'NOMINA_ELECTRONICA_AJUSTE', 'PILA_GENERACION',
    'CONTABILIZACION', 'IMPORTACION', 'LIQUIDACION_DOCUMENTO'));
