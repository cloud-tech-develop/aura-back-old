-- V202 — Conciliación fiscal, formatos 2516 y 2517 (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §5).
--
-- Una conciliación por empresa, año y formato. El balance de entrada (subido en
-- Excel o traído de la contabilidad de Aura) queda copiado aquí: la conciliación
-- es una foto del año y no cambia si después se corrige un asiento. Cada cuenta
-- cae en un renglón del formato; los ajustes (menor o mayor valor fiscal) van
-- por renglón y se marcan permanentes o temporarios (estos generan impuesto diferido).

CREATE TABLE IF NOT EXISTS conciliacion_fiscal (
    id              BIGSERIAL      PRIMARY KEY,
    empresa_id      INT            NOT NULL REFERENCES empresa(id),
    anio            INT            NOT NULL,
    -- 2516 (personas jurídicas) | 2517 (personas naturales obligadas a llevar contabilidad)
    formato         VARCHAR(4)     NOT NULL,
    tarifa_renta    NUMERIC(5,2)   NOT NULL DEFAULT 35,
    -- EXCEL | SISTEMA: de dónde salió el balance cargado
    origen          VARCHAR(10),
    cargado_at      TIMESTAMP,
    -- BORRADOR | CERRADA
    estado          VARCHAR(10)    NOT NULL DEFAULT 'BORRADOR',
    usuario_id      INT,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_conciliacion_fiscal_anio
    ON conciliacion_fiscal (empresa_id, anio, formato);

CREATE TABLE IF NOT EXISTS conciliacion_fiscal_cuenta (
    id               BIGSERIAL      PRIMARY KEY,
    conciliacion_id  BIGINT         NOT NULL REFERENCES conciliacion_fiscal(id) ON DELETE CASCADE,
    codigo           VARCHAR(20)    NOT NULL,
    nombre           VARCHAR(200),
    -- Saldo con el signo de la naturaleza de la clase (activos, costos y gastos
    -- positivos al débito; pasivos, patrimonio e ingresos positivos al crédito).
    saldo            NUMERIC(20,2)  NOT NULL DEFAULT 0,
    -- Renglón del formato; null = sin clasificar.
    renglon          VARCHAR(10),
    renglon_manual   BOOLEAN        NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_conciliacion_fiscal_cuenta
    ON conciliacion_fiscal_cuenta (conciliacion_id);

CREATE TABLE IF NOT EXISTS conciliacion_fiscal_ajuste (
    id               BIGSERIAL      PRIMARY KEY,
    conciliacion_id  BIGINT         NOT NULL REFERENCES conciliacion_fiscal(id) ON DELETE CASCADE,
    renglon          VARCHAR(10)    NOT NULL,
    -- MENOR | MAYOR (valor fiscal frente al contable)
    tipo             VARCHAR(6)     NOT NULL,
    -- PERMANENTE | TEMPORARIA
    diferencia       VARCHAR(12)    NOT NULL,
    valor            NUMERIC(20,2)  NOT NULL,
    concepto         VARCHAR(300)   NOT NULL,
    cuenta_codigo    VARCHAR(20),
    -- Clave de la sugerencia que lo originó (para no volver a sugerirla).
    sugerencia       VARCHAR(60),
    created_at       TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_conciliacion_fiscal_ajuste
    ON conciliacion_fiscal_ajuste (conciliacion_id);
