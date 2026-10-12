-- V220 — Vacaciones como módulo (rediseño de nómina R7, docs/PLAN_NOMINA_REDISENO.md §7).
--
-- Registro de vacaciones de una vinculación:
--   TIEMPO: disfrute del fecha_inicio al fecha_fin (regreso = siguiente día hábil);
--           dias_habiles se calcula sin domingos ni festivos.
--   DINERO: días compensados (máximo la mitad de lo causado, CST art. 189);
--           fecha_inicio es la fecha con la que entra a la nómina.
-- Retorno anticipado: fecha_regreso_anticipado + dias_no_disfrutados (vuelven al saldo).
-- lote: agrupa las vacaciones colectivas registradas juntas.
-- El pago entra a la nómina periódica del período como novedad VACACIONES
-- (origen VACACIONES); el saldo descuenta dias_habiles - dias_no_disfrutados.

CREATE TABLE IF NOT EXISTS nomina_vacacion (
    id                        BIGSERIAL     PRIMARY KEY,
    empresa_id                INTEGER       NOT NULL REFERENCES empresa(id),
    contrato_id               BIGINT        NOT NULL REFERENCES contrato_laboral(id),
    empleado_id               BIGINT        NOT NULL REFERENCES empleados(id),
    tipo                      VARCHAR(10)   NOT NULL,
    fecha_inicio              DATE          NOT NULL,
    fecha_fin                 DATE,
    fecha_regreso             DATE,
    dias_habiles              NUMERIC(6,2)  NOT NULL,
    fecha_regreso_anticipado  DATE,
    dias_no_disfrutados       NUMERIC(6,2)  NOT NULL DEFAULT 0,
    estado                    VARCHAR(12)   NOT NULL DEFAULT 'REGISTRADA',
    lote                      VARCHAR(40),
    observacion               VARCHAR(500),
    usuario_id                BIGINT,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP,
    CONSTRAINT ck_nomina_vacacion_tipo CHECK (tipo IN ('TIEMPO', 'DINERO')),
    CONSTRAINT ck_nomina_vacacion_estado CHECK (estado IN ('REGISTRADA', 'INTERRUMPIDA', 'ANULADA')),
    CONSTRAINT ck_nomina_vacacion_fechas CHECK (tipo = 'DINERO' OR (fecha_fin IS NOT NULL AND fecha_fin >= fecha_inicio))
);

CREATE INDEX IF NOT EXISTS idx_nomina_vacacion_contrato ON nomina_vacacion (contrato_id, fecha_inicio);

CREATE INDEX IF NOT EXISTS idx_nomina_vacacion_empresa ON nomina_vacacion (empresa_id, fecha_inicio DESC);
