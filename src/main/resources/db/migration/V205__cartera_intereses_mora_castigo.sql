-- V205 — cartera C5 (intereses de mora) y C9 (deterioro y castigo). docs/PLAN_CARTERA.md
--
-- cartera_politica: una fila por empresa. El interés es opcional (apagado por
-- defecto) y nunca pasa la tasa de usura que registra el usuario. Los tramos de
-- deterioro y los días para sugerir castigo también viven aquí.
--
-- El interés solo se causa al facturarlo: interes_mora crea una cuenta por
-- cobrar aparte (DB 1305 · CR 4210) y guarda, por cada factura, hasta qué día
-- quedó cobrado, para que el siguiente cobro empiece ahí.
--
-- El castigo saca la cuenta de la cartera activa: la cuenta pasa a 'castigada',
-- su saldo a 0 y valor_castigado guarda lo que se dio de baja (el historial de
-- abonos queda intacto). Asiento DB 1399 (hasta la provisión) · DB 5199 (resto)
-- · CR 1305.

CREATE TABLE IF NOT EXISTS cartera_politica (
    empresa_id              INTEGER       PRIMARY KEY REFERENCES empresa(id),
    interes_activo          BOOLEAN       NOT NULL DEFAULT FALSE,
    tasa_mora_mensual       NUMERIC(7,4)  NOT NULL DEFAULT 0,
    tasa_usura_mensual      NUMERIC(7,4)  NOT NULL DEFAULT 0,
    usura_vigente_hasta     DATE,
    dias_gracia             INTEGER       NOT NULL DEFAULT 0,
    deterioro_tramos        TEXT,
    dias_sugerir_castigo    INTEGER       NOT NULL DEFAULT 360,
    usuario_id              INTEGER       REFERENCES usuario(id),
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interes_mora (
    id                 BIGSERIAL     PRIMARY KEY,
    empresa_id         INTEGER       NOT NULL REFERENCES empresa(id),
    tercero_id         BIGINT        NOT NULL REFERENCES tercero(id),
    numero             VARCHAR(30)   NOT NULL,
    consecutivo        INTEGER       NOT NULL,
    fecha              DATE          NOT NULL,
    tasa_mensual       NUMERIC(7,4)  NOT NULL,
    valor              NUMERIC(15,2) NOT NULL,
    cuenta_cobrar_id   BIGINT        REFERENCES cuentas_cobrar(id),
    estado             VARCHAR(12)   NOT NULL DEFAULT 'FACTURADO',
    observaciones      VARCHAR(500),
    motivo_anulacion   VARCHAR(300),
    usuario_id         INTEGER       REFERENCES usuario(id),
    anulado_por        INTEGER       REFERENCES usuario(id),
    anulado_at         TIMESTAMP,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_interes_mora_numero UNIQUE (empresa_id, consecutivo),
    CONSTRAINT ck_interes_mora_estado CHECK (estado IN ('FACTURADO','ANULADO'))
);

CREATE INDEX IF NOT EXISTS idx_interes_mora_tercero ON interes_mora (empresa_id, tercero_id, fecha DESC);

CREATE TABLE IF NOT EXISTS interes_mora_detalle (
    id                 BIGSERIAL     PRIMARY KEY,
    interes_mora_id    BIGINT        NOT NULL REFERENCES interes_mora(id),
    cuenta_cobrar_id   BIGINT        NOT NULL REFERENCES cuentas_cobrar(id),
    desde              DATE          NOT NULL,
    hasta              DATE          NOT NULL,
    dias               INTEGER       NOT NULL,
    saldo_base         NUMERIC(15,2) NOT NULL,
    valor              NUMERIC(15,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_interes_mora_detalle_cuenta ON interes_mora_detalle (cuenta_cobrar_id);

CREATE TABLE IF NOT EXISTS cartera_castigo (
    id                 BIGSERIAL     PRIMARY KEY,
    empresa_id         INTEGER       NOT NULL REFERENCES empresa(id),
    cuenta_cobrar_id   BIGINT        NOT NULL REFERENCES cuentas_cobrar(id),
    tercero_id         BIGINT        NOT NULL REFERENCES tercero(id),
    numero             VARCHAR(30)   NOT NULL,
    consecutivo        INTEGER       NOT NULL,
    fecha              DATE          NOT NULL,
    valor              NUMERIC(15,2) NOT NULL,
    contra_provision   NUMERIC(15,2) NOT NULL DEFAULT 0,
    contra_gasto       NUMERIC(15,2) NOT NULL DEFAULT 0,
    dias_mora          INTEGER       NOT NULL DEFAULT 0,
    motivo             VARCHAR(500)  NOT NULL,
    estado             VARCHAR(12)   NOT NULL DEFAULT 'CASTIGADO',
    estado_anterior    VARCHAR(20),
    motivo_anulacion   VARCHAR(300),
    usuario_id         INTEGER       REFERENCES usuario(id),
    anulado_por        INTEGER       REFERENCES usuario(id),
    anulado_at         TIMESTAMP,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cartera_castigo_numero UNIQUE (empresa_id, consecutivo),
    CONSTRAINT ck_cartera_castigo_estado CHECK (estado IN ('CASTIGADO','ANULADO'))
);

CREATE INDEX IF NOT EXISTS idx_cartera_castigo_empresa ON cartera_castigo (empresa_id, fecha DESC);

-- Una cuenta solo puede tener un castigo vivo.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cartera_castigo_cuenta_vivo
    ON cartera_castigo (cuenta_cobrar_id) WHERE estado = 'CASTIGADO';

ALTER TABLE cuentas_cobrar ADD COLUMN IF NOT EXISTS valor_castigado NUMERIC(15,2) NOT NULL DEFAULT 0;
