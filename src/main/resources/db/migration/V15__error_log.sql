-- ─── Tabla de logs de errores HTTP ───────────────────────────
CREATE TABLE error_log (
    id          BIGSERIAL    PRIMARY KEY,
    metodo      VARCHAR(10)  NOT NULL,
    endpoint    VARCHAR(500) NOT NULL,
    status_code INT          NOT NULL,
    categoria   VARCHAR(10)  NOT NULL CHECK (categoria IN ('info', 'warn', 'danger')),
    mensaje     TEXT,
    detalle     TEXT,
    grupo_hash  VARCHAR(64)  NOT NULL,
    empresa_id  INT          REFERENCES empresa(id) ON DELETE SET NULL,
    usuario_nombre VARCHAR(200),
    ip_origen   VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_error_log_categoria   ON error_log (categoria);
CREATE INDEX idx_error_log_grupo_hash  ON error_log (grupo_hash);
CREATE INDEX idx_error_log_empresa_id  ON error_log (empresa_id);
CREATE INDEX idx_error_log_created_at  ON error_log (created_at);
CREATE INDEX idx_error_log_status_code ON error_log (status_code);
