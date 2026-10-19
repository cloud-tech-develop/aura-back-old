-- V229 — Cuenta de cobro y envío de documentos por correo (docs/PLAN_CUENTA_COBRO_CORREO.md).
--
-- · Cuenta de cobro: la persona natural no responsable de IVA no está obligada a
--   facturar electrónicamente; cobra con una cuenta de cobro. Vive en Facturas de
--   venta (factura_venta.tipo) y al emitirse crea la venta con
--   tipo_documento = 'CUENTA_COBRO', consecutivo propio (prefijo CC), sin IVA y sin
--   DIAN. Entra a cartera y a contabilidad igual que una factura.
-- · contrato_dos_trabajadores: declaración del art. 383 ET (si contrató o no dos o
--   más trabajadores) para que el cliente sepa qué retención aplicar. NULL = no declara.
-- · correo_envio: cada correo que sale (factura, cuenta de cobro, cotización,
--   comprobante de nómina) con su estado. El adjunto se guarda solo mientras está
--   PENDIENTE (los envíos masivos van en cola) y se borra al enviarse.

ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS tipo VARCHAR(15) NOT NULL DEFAULT 'FACTURA';
ALTER TABLE factura_venta ADD COLUMN IF NOT EXISTS contrato_dos_trabajadores BOOLEAN;

CREATE TABLE IF NOT EXISTS correo_envio (
    id               BIGSERIAL     PRIMARY KEY,
    empresa_id       INT           NOT NULL REFERENCES empresa(id),
    -- Envíos masivos: todos los correos del mismo lote comparten este id.
    lote_id          VARCHAR(36),
    -- FACTURA | CUENTA_COBRO | COTIZACION | NOMINA
    tipo             VARCHAR(20)   NOT NULL,
    documento_id     BIGINT        NOT NULL,
    destinatario     VARCHAR(500)  NOT NULL,
    cc               VARCHAR(500),
    asunto           VARCHAR(200)  NOT NULL,
    mensaje          TEXT,
    adjunto_nombre   VARCHAR(150),
    adjunto          BYTEA,
    -- PENDIENTE | ENVIADO | FALLIDO
    estado           VARCHAR(12)   NOT NULL DEFAULT 'PENDIENTE',
    intentos         INT           NOT NULL DEFAULT 0,
    proveedor_id     VARCHAR(80),
    error            VARCHAR(500),
    usuario_id       INT,
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    enviado_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_correo_envio_documento ON correo_envio (empresa_id, tipo, documento_id);
CREATE INDEX IF NOT EXISTS idx_correo_envio_lote ON correo_envio (lote_id) WHERE lote_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_correo_envio_pendiente ON correo_envio (id) WHERE estado = 'PENDIENTE';
