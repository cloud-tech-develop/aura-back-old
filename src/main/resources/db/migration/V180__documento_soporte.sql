-- V180 — Documento soporte electrónico (compras a no obligados a facturar).
--
-- Sin documento soporte, la compra o el gasto a un proveedor que no factura
-- (persona natural no responsable de IVA, casi siempre) no es deducible en
-- renta ni el IVA es descontable. El documento se genera DESDE la compra o el
-- gasto ya registrados: su asiento ya existe, esto es solo el soporte fiscal
-- ante la DIAN vía Factus v1 (/v1/support-documents/validate).
--
-- Se guarda cada intento (también los rechazados, con la respuesta de Factus)
-- para que el usuario vea por qué falló sin volver a enviarlo a ciegas.

CREATE TABLE IF NOT EXISTS documento_soporte (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         INT          NOT NULL,
    -- COMPRA | GASTO
    origen_tipo        VARCHAR(20)  NOT NULL,
    origen_id          BIGINT       NOT NULL,
    tercero_id         BIGINT,
    reference_code     VARCHAR(60)  NOT NULL,
    numbering_range_id VARCHAR(20),
    numero             VARCHAR(40),
    -- CUDS: código único del documento soporte
    cude               VARCHAR(200),
    -- ACEPTADO | RECHAZADO | ELIMINADO
    estado             VARCHAR(20)  NOT NULL,
    total              NUMERIC(18,2),
    retenciones        NUMERIC(18,2),
    payload_json       TEXT,
    response_json      TEXT,
    mensaje_error      TEXT,
    usuario_id         INT,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_documento_soporte_reference
    ON documento_soporte (empresa_id, reference_code);

-- Un documento soporte aceptado por origen: emitir dos veces la misma compra
-- duplicaría el costo deducible ante la DIAN.
CREATE UNIQUE INDEX IF NOT EXISTS ux_documento_soporte_origen_aceptado
    ON documento_soporte (empresa_id, origen_tipo, origen_id)
    WHERE estado = 'ACEPTADO';

CREATE INDEX IF NOT EXISTS idx_documento_soporte_empresa_fecha
    ON documento_soporte (empresa_id, created_at);
