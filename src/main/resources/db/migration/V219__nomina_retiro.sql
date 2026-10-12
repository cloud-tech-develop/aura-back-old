-- V219 — Retiros (rediseño de nómina R5, docs/PLAN_NOMINA_REDISENO.md §6).
--
-- Registrar un retiro termina la vinculación en la fecha de retiro (PILA reporta
-- RET por esa fecha de fin) y deja el retiro pendiente de liquidar. Desde el retiro
-- se genera la prenómina de liquidación definitiva (documento_id). El estado que ve
-- el usuario (pendiente, en liquidación, liquidado, pagado) se deduce del documento;
-- aquí solo se guarda si el retiro sigue vigente o se anuló.
--
-- fecha_fin_pactada: la fecha de fin que tenía la vinculación antes del retiro
--   (para la indemnización de los contratos a término fijo y para reactivarla al
--   anular).
-- cesantias_corte_hasta: cesantías ya consignadas al fondo fuera del sistema hasta
--   esa fecha; la liquidación no las vuelve a pagar.

CREATE TABLE IF NOT EXISTS nomina_retiro (
    id                     BIGSERIAL     PRIMARY KEY,
    empresa_id             INTEGER       NOT NULL REFERENCES empresa(id),
    contrato_id            BIGINT        NOT NULL REFERENCES contrato_laboral(id),
    empleado_id            BIGINT        NOT NULL REFERENCES empleados(id),
    fecha_retiro           DATE          NOT NULL,
    motivo_id              BIGINT        NOT NULL REFERENCES nomina_catalogo(id),
    indemniza              BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha_fin_pactada      DATE,
    cesantias_corte_hasta  DATE,
    observacion            VARCHAR(500),
    estado                 VARCHAR(12)   NOT NULL DEFAULT 'VIGENTE',
    documento_id           BIGINT        REFERENCES nomina_documento(id),
    usuario_id             BIGINT,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP,
    anulado_at             TIMESTAMP,
    CONSTRAINT ck_nomina_retiro_estado CHECK (estado IN ('VIGENTE', 'ANULADO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_retiro_contrato_vigente
    ON nomina_retiro (contrato_id) WHERE estado = 'VIGENTE';

CREATE INDEX IF NOT EXISTS idx_nomina_retiro_empresa_fecha
    ON nomina_retiro (empresa_id, fecha_retiro DESC);
