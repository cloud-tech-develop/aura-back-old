-- V63: Comprobante contable manual
-- El "comprobante" reutiliza asiento_contable (cabecera + asiento_detalle = líneas).
-- Se agregan los campos de cabecera propios de un comprobante elaborado a mano:
-- tipo, beneficiario y sus datos, ciudad de emisión y vencimiento.
-- Las líneas (cuenta, débito, crédito, tercero, centro de costo) ya existen en
-- asiento_detalle. Las retenciones y el cruce de anticipos son líneas adicionales.

ALTER TABLE asiento_contable
    -- Tipo de comprobante: CD=Diario, CE=Egreso, RC=Ingreso/Recibo de caja.
    -- Determina el prefijo del consecutivo (numero_comprobante).
    ADD COLUMN IF NOT EXISTS tipo_comprobante        VARCHAR(20),
    -- Beneficiario (tercero) y snapshot de sus datos para impresión.
    ADD COLUMN IF NOT EXISTS beneficiario_tercero_id BIGINT,
    ADD COLUMN IF NOT EXISTS beneficiario_nombre     VARCHAR(200),
    ADD COLUMN IF NOT EXISTS beneficiario_direccion  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS beneficiario_telefono   VARCHAR(50),
    -- Ciudad donde se genera el comprobante.
    ADD COLUMN IF NOT EXISTS ciudad                  VARCHAR(100),
    -- Fecha de vencimiento del comprobante (opcional).
    ADD COLUMN IF NOT EXISTS fecha_vencimiento       DATE;

-- Consecutivo único por empresa (evita duplicar numero_comprobante en concurrencia).
-- Parcial: solo aplica a filas que ya tienen consecutivo asignado.
CREATE UNIQUE INDEX IF NOT EXISTS ux_asiento_empresa_comprobante
    ON asiento_contable (empresa_id, numero_comprobante)
    WHERE numero_comprobante IS NOT NULL;

-- Índice para listar/filtrar comprobantes por tipo.
CREATE INDEX IF NOT EXISTS ix_asiento_tipo_comprobante
    ON asiento_contable (empresa_id, tipo_comprobante);
