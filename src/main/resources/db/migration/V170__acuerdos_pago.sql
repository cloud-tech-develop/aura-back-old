-- V170 — acuerdos de pago en cuotas.
--
-- La deuda de una o varias cuentas por cobrar se reparte en cuotas con fecha y
-- valor. Los pagos siguen entrando como abonos a las cuentas (arqueo, asientos y
-- reportes no cambian): lo que baja el saldo de las cuentas desde el acuerdo se
-- aplica a las cuotas en orden. Mientras el acuerdo está vivo, el vencimiento de
-- sus cuentas es el de la primera cuota sin pagar, así la mora y las edades se
-- miden por cuota; acuerdo_pago_cuenta guarda el vencimiento original para
-- devolverlo si el acuerdo se anula.

CREATE TABLE IF NOT EXISTS acuerdo_pago (
    id                BIGSERIAL     PRIMARY KEY,
    empresa_id        INTEGER       NOT NULL REFERENCES empresa(id),
    tercero_id        BIGINT        NOT NULL REFERENCES tercero(id),
    numero            VARCHAR(30)   NOT NULL,
    consecutivo       INTEGER       NOT NULL,
    valor_total       NUMERIC(15,2) NOT NULL,
    valor_pagado      NUMERIC(15,2) NOT NULL DEFAULT 0,
    numero_cuotas     INTEGER       NOT NULL,
    frecuencia        VARCHAR(15)   NOT NULL DEFAULT 'MENSUAL',
    dias_gracia       INTEGER       NOT NULL DEFAULT 0,
    estado            VARCHAR(12)   NOT NULL DEFAULT 'VIGENTE',
    observaciones     VARCHAR(500),
    motivo_anulacion  VARCHAR(300),
    usuario_id        INTEGER       REFERENCES usuario(id),
    anulado_por       INTEGER       REFERENCES usuario(id),
    anulado_at        TIMESTAMP,
    incumplido_at     TIMESTAMP,
    cumplido_at       TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT uq_acuerdo_pago_numero UNIQUE (empresa_id, consecutivo),
    CONSTRAINT ck_acuerdo_pago_estado CHECK (estado IN ('VIGENTE','INCUMPLIDO','CUMPLIDO','ANULADO')),
    CONSTRAINT ck_acuerdo_pago_frecuencia CHECK (frecuencia IN ('SEMANAL','QUINCENAL','MENSUAL','PERSONALIZADA'))
);

CREATE INDEX IF NOT EXISTS idx_acuerdo_pago_tercero ON acuerdo_pago (empresa_id, tercero_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_acuerdo_pago_estado ON acuerdo_pago (empresa_id, estado);

CREATE TABLE IF NOT EXISTS acuerdo_pago_cuenta (
    id                          BIGSERIAL     PRIMARY KEY,
    acuerdo_pago_id             BIGINT        NOT NULL REFERENCES acuerdo_pago(id),
    cuenta_cobrar_id            BIGINT        NOT NULL REFERENCES cuentas_cobrar(id),
    saldo_inicial               NUMERIC(15,2) NOT NULL,
    fecha_vencimiento_original  TIMESTAMP,
    activo                      BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_acuerdo_pago_cuenta_acuerdo ON acuerdo_pago_cuenta (acuerdo_pago_id);

-- Una cuenta solo puede estar en un acuerdo vivo a la vez.
CREATE UNIQUE INDEX IF NOT EXISTS uq_acuerdo_pago_cuenta_activa
    ON acuerdo_pago_cuenta (cuenta_cobrar_id) WHERE activo;

CREATE TABLE IF NOT EXISTS acuerdo_pago_cuota (
    id                 BIGSERIAL     PRIMARY KEY,
    acuerdo_pago_id    BIGINT        NOT NULL REFERENCES acuerdo_pago(id),
    numero             INTEGER       NOT NULL,
    fecha_vencimiento  DATE          NOT NULL,
    valor              NUMERIC(15,2) NOT NULL,
    valor_pagado       NUMERIC(15,2) NOT NULL DEFAULT 0,
    estado             VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE',
    pagada_at          TIMESTAMP,
    CONSTRAINT uq_acuerdo_pago_cuota UNIQUE (acuerdo_pago_id, numero),
    CONSTRAINT ck_acuerdo_pago_cuota_estado CHECK (estado IN ('PENDIENTE','PARCIAL','PAGADA','VENCIDA'))
);

CREATE INDEX IF NOT EXISTS idx_acuerdo_pago_cuota_fecha ON acuerdo_pago_cuota (fecha_vencimiento, estado);
