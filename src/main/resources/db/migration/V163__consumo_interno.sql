-- ── V163: consumo interno (el negocio usa su propio inventario) ────────────
--
-- Una ferretería gasta tornillos arreglando el local, un supermercado usa sus
-- bolsas y su jabón, una tienda saca café para el personal. No es merma (no se
-- perdió nada) ni obsequio (no salió a un tercero): es un gasto del negocio
-- que hoy se registraba como pérdida o no se registraba.
--
-- El asiento que genera el documento:
--   DB cuenta del concepto (o 5195 si no tiene)  ·  CR inventario   (costo)
-- y, si `genera_iva`:
--   DB 529505 IVA asumido en retiro  ·  CR IVA generado            (IVA)
--
-- El IVA existe porque el retiro de bienes para uso propio se trata como
-- venta para efectos de IVA (art. 421 lit. b E.T.), salvo excepciones que
-- decide el contador. Por eso lo trae cada concepto y se puede cambiar en
-- cada documento.
--
-- Plan: docs/PLAN_LOTES_SERIALES_CONSUMO_INTERNO.md (F5).

-- Para qué se usó. Cada empresa tiene su lista y su cuenta de gasto.
CREATE TABLE IF NOT EXISTS concepto_consumo_interno (
    id          BIGSERIAL    PRIMARY KEY,
    empresa_id  INTEGER      NOT NULL,
    nombre      VARCHAR(80)  NOT NULL,
    -- Cuenta del gasto (o del activo). NULL = la de ConceptoContable
    -- GASTO_CONSUMO_INTERNO (5195 por defecto).
    cuenta_id   BIGINT,
    -- Valor inicial de "¿Genera IVA?" en el documento.
    genera_iva  BOOLEAN      NOT NULL DEFAULT TRUE,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_concepto_consumo_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_concepto_consumo_cuenta  FOREIGN KEY (cuenta_id)  REFERENCES plan_cuenta(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_concepto_consumo_nombre
    ON concepto_consumo_interno (empresa_id, LOWER(nombre));

CREATE TABLE IF NOT EXISTS consumo_interno (
    id                     BIGSERIAL     PRIMARY KEY,
    empresa_id             INTEGER       NOT NULL,
    sucursal_id            INTEGER       NOT NULL,
    usuario_id             INTEGER,
    concepto_id            BIGINT        NOT NULL,
    -- Quién lo retiró o para quién (un empleado, el área). Opcional.
    responsable_tercero_id BIGINT,
    fecha                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacion            VARCHAR(300),
    costo_total            NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Valor comercial sin IVA: base del IVA por retiro.
    base_comercial_total   NUMERIC(15,2) NOT NULL DEFAULT 0,
    iva_total              NUMERIC(15,2) NOT NULL DEFAULT 0,
    genera_iva             BOOLEAN       NOT NULL DEFAULT TRUE,
    estado                 VARCHAR(20)   NOT NULL DEFAULT 'APROBADO',
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_consumo_interno_estado CHECK (estado IN ('APROBADO', 'ANULADO')),
    CONSTRAINT fk_consumo_interno_empresa     FOREIGN KEY (empresa_id)  REFERENCES empresa(id),
    CONSTRAINT fk_consumo_interno_sucursal    FOREIGN KEY (sucursal_id) REFERENCES sucursal(id),
    CONSTRAINT fk_consumo_interno_concepto    FOREIGN KEY (concepto_id) REFERENCES concepto_consumo_interno(id),
    CONSTRAINT fk_consumo_interno_responsable FOREIGN KEY (responsable_tercero_id) REFERENCES tercero(id)
);

CREATE TABLE IF NOT EXISTS consumo_interno_detalle (
    id                       BIGSERIAL     PRIMARY KEY,
    consumo_interno_id       BIGINT        NOT NULL,
    producto_id              BIGINT        NOT NULL,
    lote_id                  BIGINT,
    -- Presentación en que se escribió la línea (1 Bulto). `cantidad` va
    -- siempre en unidad base, igual que merma y obsequio (V162).
    producto_presentacion_id BIGINT,
    cantidad_presentacion    NUMERIC(18,6),
    cantidad                 NUMERIC(18,6) NOT NULL,
    -- Congelado al registrar: el asiento no se mueve si el costo cambia.
    costo_unitario           NUMERIC(15,2) NOT NULL DEFAULT 0,
    base_comercial_unitaria  NUMERIC(15,2) NOT NULL DEFAULT 0,
    iva_valor                NUMERIC(15,2) NOT NULL DEFAULT 0,

    CONSTRAINT fk_consumo_detalle_consumo      FOREIGN KEY (consumo_interno_id)       REFERENCES consumo_interno(id),
    CONSTRAINT fk_consumo_detalle_producto     FOREIGN KEY (producto_id)              REFERENCES producto(id),
    CONSTRAINT fk_consumo_detalle_lote         FOREIGN KEY (lote_id)                  REFERENCES lote(id),
    CONSTRAINT fk_consumo_detalle_presentacion FOREIGN KEY (producto_presentacion_id) REFERENCES producto_presentacion(id)
);

CREATE INDEX IF NOT EXISTS idx_consumo_interno_empresa ON consumo_interno (empresa_id, fecha);
CREATE INDEX IF NOT EXISTS idx_consumo_interno_detalle ON consumo_interno_detalle (consumo_interno_id);

-- Un producto con receta consumido internamente saca sus componentes, igual
-- que en merma y obsequio (V157).
ALTER TABLE inventario_consumo_componente
    DROP CONSTRAINT IF EXISTS chk_consumo_componente_origen;
ALTER TABLE inventario_consumo_componente
    ADD CONSTRAINT chk_consumo_componente_origen
    CHECK (origen IN ('MERMA', 'OBSEQUIO', 'VENTA', 'CONSUMO_INTERNO'));

-- "Pasar a unidad" (V160) bloquea anular documentos anteriores al cambio.
ALTER TABLE producto_cambio_unidad
    ADD COLUMN IF NOT EXISTS ultimo_consumo_interno_id BIGINT NOT NULL DEFAULT 0;

-- Conceptos iniciales para las empresas que ya existen. Las nuevas los reciben
-- la primera vez que abren la pantalla (ConsumoInternoServiceImpl).
INSERT INTO concepto_consumo_interno (empresa_id, nombre, genera_iva)
SELECT e.id, v.nombre, TRUE
  FROM empresa e
 CROSS JOIN (VALUES
        ('Aseo y cafetería'),
        ('Papelería y útiles'),
        ('Mantenimiento del local'),
        ('Dotación y consumo del personal'),
        ('Exhibición y decoración'),
        ('Otro')
  ) AS v(nombre)
 WHERE NOT EXISTS (
        SELECT 1 FROM concepto_consumo_interno c WHERE c.empresa_id = e.id);
