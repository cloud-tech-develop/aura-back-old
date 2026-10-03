-- ── V187: traslado de cuentas y fusión de terceros (Fase 4) ───────────────
--
-- Dos herramientas del contador, con bitácora de quién, cuándo y cuántos
-- registros movió:
--   · traslado de cuentas: mover los movimientos de una cuenta a otra en un
--     rango de fechas (solo en períodos abiertos);
--   · fusión de terceros: dejar un solo tercero cuando hay duplicados.
--
-- Idempotente. Espejo en Laravel: 2026_07_03_000187_traslado_cuentas_fusion_terceros.php

CREATE TABLE IF NOT EXISTS traslado_cuenta_log (
    id                BIGSERIAL    PRIMARY KEY,
    empresa_id        INT          NOT NULL,
    cuenta_origen_id  BIGINT       NOT NULL,
    cuenta_destino_id BIGINT       NOT NULL,
    desde             DATE         NOT NULL,
    hasta             DATE         NOT NULL,
    tercero_id        BIGINT,
    lineas_movidas    INT          NOT NULL,
    motivo            VARCHAR(300) NOT NULL,
    usuario_id        BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_traslado_cuenta_log_empresa ON traslado_cuenta_log (empresa_id, created_at DESC);

CREATE TABLE IF NOT EXISTS fusion_tercero_log (
    id                 BIGSERIAL    PRIMARY KEY,
    empresa_id         INT          NOT NULL,
    tercero_origen_id  BIGINT       NOT NULL,
    tercero_destino_id BIGINT       NOT NULL,
    origen_documento   VARCHAR(40),
    origen_nombre      VARCHAR(250),
    -- tabla.columna → filas movidas, en JSON
    detalle            TEXT,
    registros_movidos  INT          NOT NULL,
    motivo             VARCHAR(300) NOT NULL,
    usuario_id         BIGINT,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_fusion_tercero_log_empresa ON fusion_tercero_log (empresa_id, created_at DESC);
