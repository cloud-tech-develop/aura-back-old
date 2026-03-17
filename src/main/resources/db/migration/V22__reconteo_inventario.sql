-- ── V22: Módulo de Reconteo de Inventario ────────────────────────────────────

-- Cabecera del reconteo (una sesión de conteo físico por sucursal)
CREATE TABLE reconteos (
    id              BIGSERIAL PRIMARY KEY,
    empresa_id      INT          NOT NULL REFERENCES empresa(id),
    sucursal_id     INT          NOT NULL REFERENCES sucursal(id),
    estado          VARCHAR(30)  NOT NULL DEFAULT 'BORRADOR',
                    -- BORRADOR | EN_CONTEO | APROBADO | ANULADO
    tipo            VARCHAR(20)  NOT NULL DEFAULT 'TOTAL',
                    -- TOTAL | PARCIAL
    observaciones   VARCHAR(500),
    creado_por_id   INT          REFERENCES usuario(id),
    aprobado_por_id INT          REFERENCES usuario(id),
    fecha_inicio    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_reconteo_estado
        CHECK (estado IN ('BORRADOR', 'EN_CONTEO', 'APROBADO', 'ANULADO')),
    CONSTRAINT chk_reconteo_tipo
        CHECK (tipo IN ('TOTAL', 'PARCIAL'))
);

CREATE INDEX idx_reconteos_empresa    ON reconteos(empresa_id);
CREATE INDEX idx_reconteos_sucursal   ON reconteos(sucursal_id, empresa_id);
CREATE INDEX idx_reconteos_estado     ON reconteos(estado, empresa_id);

-- ─────────────────────────────────────────────────────────────────────────────

-- Detalle: una línea por producto (snapshot del stock + lo que se cuenta físicamente)
CREATE TABLE reconteo_detalles (
    id              BIGSERIAL    PRIMARY KEY,
    reconteo_id     BIGINT       NOT NULL REFERENCES reconteos(id) ON DELETE CASCADE,
    producto_id     BIGINT       NOT NULL REFERENCES producto(id),
    lote_id         BIGINT       REFERENCES lote(id),
    stock_sistema   NUMERIC(15,4) NOT NULL DEFAULT 0,
                    -- Snapshot del stock al momento de crear el reconteo
    stock_contado   NUMERIC(15,4),
                    -- NULL = aún no contado; se llena al hacer el conteo físico
    ajuste_aplicado BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX idx_reconteo_detalles_reconteo  ON reconteo_detalles(reconteo_id);
CREATE INDEX idx_reconteo_detalles_producto  ON reconteo_detalles(producto_id, reconteo_id);
