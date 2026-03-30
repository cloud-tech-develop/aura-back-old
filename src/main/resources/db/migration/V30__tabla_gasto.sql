CREATE TABLE IF NOT EXISTS gasto (
    id          BIGSERIAL   PRIMARY KEY,
    empresa_id  INTEGER     NOT NULL REFERENCES empresa(id),
    sucursal_id INTEGER     REFERENCES sucursal(id),
    usuario_id  INTEGER     REFERENCES usuario(id),
    categoria   VARCHAR(100) NOT NULL,
    descripcion TEXT,
    monto       NUMERIC(15,2) NOT NULL DEFAULT 0,
    fecha       DATE          NOT NULL DEFAULT CURRENT_DATE,
    deducible   BOOLEAN       NOT NULL DEFAULT FALSE,
    estado      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
