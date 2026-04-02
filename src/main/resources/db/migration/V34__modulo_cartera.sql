-- ═══════════════════════════════════════════════════════════════════
-- V34 — Módulo Cartera: crédito, scoring, cobros, alertas
-- ═══════════════════════════════════════════════════════════════════

-- 1. Perfil de crédito por cliente
CREATE TABLE tercero_credito (
    id                    BIGSERIAL PRIMARY KEY,
    empresa_id            INTEGER       NOT NULL REFERENCES empresa(id),
    tercero_id            BIGINT        NOT NULL REFERENCES tercero(id),
    cupo_credito_inicial  NUMERIC(15,2) NOT NULL DEFAULT 0,
    cupo_credito_actual   NUMERIC(15,2) NOT NULL DEFAULT 0,
    plazo_dias            INTEGER       NOT NULL DEFAULT 30,
    estado_credito        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
        -- ACTIVO | SUSPENDIDO | BLOQUEADO | EN_ESTUDIO
    nivel_riesgo          VARCHAR(20)   NOT NULL DEFAULT 'BAJO',
        -- BAJO | MEDIO | ALTO | CRITICO
    score_crediticio      INTEGER       NOT NULL DEFAULT 500, -- 0-1000
    requiere_autorizacion BOOLEAN       NOT NULL DEFAULT FALSE,
    dias_mora_tolerancia  INTEGER       NOT NULL DEFAULT 30,
    fecha_ultimo_estudio  TIMESTAMP,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP,
    UNIQUE (empresa_id, tercero_id)
);

CREATE INDEX idx_tercero_credito_empresa ON tercero_credito(empresa_id);
CREATE INDEX idx_tercero_credito_tercero ON tercero_credito(tercero_id);

-- 2. Historial de eventos de crédito
CREATE TABLE historial_credito (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     INTEGER     NOT NULL REFERENCES empresa(id),
    tercero_id     BIGINT      NOT NULL REFERENCES tercero(id),
    tipo_evento    VARCHAR(30) NOT NULL,
        -- APERTURA | AUMENTO_CUPO | REDUCCION_CUPO | BLOQUEO |
        -- DESBLOQUEO | ESTUDIO | MORA_DETECTADA | NORMALIZACION
    cupo_anterior  NUMERIC(15,2),
    cupo_nuevo     NUMERIC(15,2),
    score_anterior INTEGER,
    score_nuevo    INTEGER,
    motivo         TEXT,
    usuario_id     INTEGER REFERENCES usuario(id),
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_historial_credito_tercero ON historial_credito(empresa_id, tercero_id);

-- 3. Reglas empresariales del motor de crédito
CREATE TABLE regla_credito (
    id              BIGSERIAL    PRIMARY KEY,
    empresa_id      INTEGER      NOT NULL REFERENCES empresa(id),
    nombre          VARCHAR(100) NOT NULL,
    tipo            VARCHAR(30)  NOT NULL,
        -- BLOQUEO | ALERTA | AUMENTO_CUPO | REDUCCION_CUPO
    evento          VARCHAR(30)  NOT NULL,
        -- AL_VENDER | AL_PAGAR | PERIODICO
    condicion_json  JSONB        NOT NULL DEFAULT '{}',
        -- {"pagos_consecutivos_a_tiempo": 3, "score_minimo": 700, "sin_mora_dias": 90}
    accion_json     JSONB        NOT NULL DEFAULT '{}',
        -- {"aumentar_pct": 10, "cupo_maximo": 5000000} | {"bloquear": true}
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    orden           INTEGER      NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_regla_credito_empresa ON regla_credito(empresa_id, activo);

-- 4. Log de scores calculados
CREATE TABLE score_crediticio_log (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     INTEGER   NOT NULL REFERENCES empresa(id),
    tercero_id     BIGINT    NOT NULL REFERENCES tercero(id),
    score          INTEGER   NOT NULL,
    factores_json  JSONB     NOT NULL DEFAULT '{}',
        -- {"historial_pago": 380, "utilizacion": 210, "antiguedad": 120, "mora": -100}
    calculated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_score_log_tercero ON score_crediticio_log(empresa_id, tercero_id, calculated_at DESC);

-- 5. Solicitudes de autorización cuando se supera el cupo
CREATE TABLE solicitud_autorizacion_credito (
    id                BIGSERIAL    PRIMARY KEY,
    empresa_id        INTEGER      NOT NULL REFERENCES empresa(id),
    tercero_id        BIGINT       NOT NULL REFERENCES tercero(id),
    venta_id          BIGINT       REFERENCES venta(id),
    monto_solicitado  NUMERIC(15,2) NOT NULL,
    cupo_disponible   NUMERIC(15,2) NOT NULL,
    excedente         NUMERIC(15,2) NOT NULL,
    estado            VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
        -- PENDIENTE | APROBADA | RECHAZADA
    aprobado_por_id   INTEGER      REFERENCES usuario(id),
    motivo_rechazo    TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP
);

CREATE INDEX idx_solicitud_credito_empresa ON solicitud_autorizacion_credito(empresa_id, estado);

-- 6. Gestión de cobros y notas sobre cuentas vencidas
CREATE TABLE gestion_cobro (
    id                BIGSERIAL    PRIMARY KEY,
    empresa_id        INTEGER      NOT NULL REFERENCES empresa(id),
    tercero_id        BIGINT       NOT NULL REFERENCES tercero(id),
    cuenta_cobrar_id  BIGINT       REFERENCES cuentas_cobrar(id),
    tipo_gestion      VARCHAR(30)  NOT NULL,
        -- LLAMADA | EMAIL | VISITA | NOTA | ACUERDO_PAGO | MENSAJE
    resultado         VARCHAR(30),
        -- CONTACTADO | NO_CONTESTO | PROMESA_PAGO | RENUENTE | PAGADO
    nota              TEXT,
    fecha_promesa_pago DATE,
    monto_prometido   NUMERIC(15,2),
    usuario_id        INTEGER      REFERENCES usuario(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gestion_cobro_tercero  ON gestion_cobro(empresa_id, tercero_id);
CREATE INDEX idx_gestion_cobro_cuenta   ON gestion_cobro(cuenta_cobrar_id);
