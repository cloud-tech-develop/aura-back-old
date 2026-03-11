-- ─── Token de reset de contraseña ────────────────────────────
CREATE TABLE password_reset_token (
    id         BIGSERIAL    PRIMARY KEY,
    usuario_id INT          NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token      VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    usado      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prt_token ON password_reset_token (token);
