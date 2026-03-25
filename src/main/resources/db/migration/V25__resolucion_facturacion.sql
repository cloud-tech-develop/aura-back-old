-- Campos de resolución de facturación electrónica en empresa
ALTER TABLE empresa
    ADD COLUMN IF NOT EXISTS resolucion_numero     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS resolucion_prefijo    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS resolucion_desde      INT,
    ADD COLUMN IF NOT EXISTS resolucion_hasta      INT,
    ADD COLUMN IF NOT EXISTS resolucion_fecha_desde VARCHAR(20),
    ADD COLUMN IF NOT EXISTS resolucion_fecha_hasta VARCHAR(20);
