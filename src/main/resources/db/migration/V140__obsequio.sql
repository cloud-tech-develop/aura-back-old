-- ── V140: obsequios (retiro de inventario sin contraprestación) ─────────────
--
-- Regalar un producto no es una venta con 100% de descuento: no hay ingreso,
-- pero sí sale inventario y sí hay un costo que tiene que aterrizar en algún
-- lado. Hasta hoy la única salida sin venta era la merma, que lo clasificaba
-- como pérdida — mezclando lo que se daña con lo que se regala, dos cosas que
-- el negocio necesita medir por separado.
--
-- El asiento que genera este documento:
--   DB 523550 Obsequios y muestras   ·  CR 1435 Inventario     (por el costo)
-- y, si `genera_iva`:
--   DB 529505 IVA asumido en retiro  ·  CR 240801 IVA generado (por el IVA)
--
-- El segundo par existe porque el retiro de inventario se considera venta para
-- efectos de IVA: el impuesto se causa sobre el valor comercial aunque no se
-- cobre nada. Es opcional por documento porque el tratamiento depende del tipo
-- de obsequio y esa decisión es del contador, no del motor.

CREATE TABLE IF NOT EXISTS obsequio (
    id                   BIGSERIAL PRIMARY KEY,
    empresa_id           INTEGER      NOT NULL,
    sucursal_id          INTEGER      NOT NULL,
    usuario_id           INTEGER,
    -- A quién se le entregó. Opcional: una muestra en punto de venta no
    -- siempre tiene destinatario identificado.
    tercero_id           BIGINT,
    fecha                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Para qué se regaló: es lo que después permite medir "cuánto invertí en
    -- promoción" contra "cuánto regalé por cortesía".
    motivo               VARCHAR(30)  NOT NULL,
    observacion          VARCHAR(300),
    -- Costo de lo entregado: lo que va al gasto y sale del inventario.
    costo_total          NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Valor comercial sin IVA: base sobre la que se causa el IVA del retiro.
    base_comercial_total NUMERIC(15,2) NOT NULL DEFAULT 0,
    iva_total            NUMERIC(15,2) NOT NULL DEFAULT 0,
    genera_iva           BOOLEAN      NOT NULL DEFAULT TRUE,
    estado               VARCHAR(20)  NOT NULL DEFAULT 'APROBADO',
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_obsequio_motivo CHECK (motivo IN
        ('MUESTRA_COMERCIAL', 'PROMOCION', 'CORTESIA_CLIENTE', 'DONACION', 'OTRO')),
    CONSTRAINT chk_obsequio_estado CHECK (estado IN ('APROBADO', 'ANULADO')),
    CONSTRAINT fk_obsequio_empresa  FOREIGN KEY (empresa_id)  REFERENCES empresa(id),
    CONSTRAINT fk_obsequio_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id),
    CONSTRAINT fk_obsequio_tercero  FOREIGN KEY (tercero_id)  REFERENCES tercero(id)
);

CREATE TABLE IF NOT EXISTS obsequio_detalle (
    id                        BIGSERIAL PRIMARY KEY,
    obsequio_id               BIGINT        NOT NULL,
    producto_id               BIGINT        NOT NULL,
    lote_id                   BIGINT,
    cantidad                  NUMERIC(18,6) NOT NULL,
    -- Costo unitario congelado al momento de entregar (igual que venta_detalle:
    -- el asiento no puede depender de que el costo del producto cambie después).
    costo_unitario            NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Valor comercial unitario SIN IVA, base del impuesto por retiro.
    base_comercial_unitaria   NUMERIC(15,2) NOT NULL DEFAULT 0,
    iva_valor                 NUMERIC(15,2) NOT NULL DEFAULT 0,

    CONSTRAINT fk_obsequio_detalle_obsequio FOREIGN KEY (obsequio_id) REFERENCES obsequio(id),
    CONSTRAINT fk_obsequio_detalle_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_obsequio_detalle_lote     FOREIGN KEY (lote_id)     REFERENCES lote(id)
);

CREATE INDEX IF NOT EXISTS idx_obsequio_empresa  ON obsequio (empresa_id, fecha);
CREATE INDEX IF NOT EXISTS idx_obsequio_detalle  ON obsequio_detalle (obsequio_id);

-- ── Cuentas del PUC que necesita el asiento ─────────────────────────────────
-- `seedPUC` ya las siembra para las empresas nuevas; estas son las que ya
-- existen, que si no las tienen verían fallar el posting con
-- CuentaNoParametrizada. Se insertan respetando la jerarquía (padre primero) y
-- solo donde falten, empresa por empresa.
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT e.id, v.codigo, v.nombre, 'GASTO', 'DEBITO', v.nivel,
       (SELECT p.id FROM plan_cuenta p
         WHERE p.empresa_id = e.id AND p.codigo = v.codigo_padre LIMIT 1),
       TRUE, v.nivel >= 3, CURRENT_TIMESTAMP
  FROM empresa e
  CROSS JOIN (VALUES
        (1, '52',     'Gastos Operacionales de Ventas',      2::SMALLINT, '5'),
        (2, '5235',   'Servicios',                           3::SMALLINT, '52'),
        (3, '523550', 'Publicidad Propaganda y Promocion',   4::SMALLINT, '5235'),
        (4, '5295',   'Diversos',                            3::SMALLINT, '52'),
        (5, '529505', 'IVA Asumido en Retiro de Inventario', 4::SMALLINT, '5295')
  ) AS v(orden, codigo, nombre, nivel, codigo_padre)
 WHERE NOT EXISTS (
        SELECT 1 FROM plan_cuenta p
         WHERE p.empresa_id = e.id AND p.codigo = v.codigo)
   -- Solo empresas que ya tienen PUC sembrado: si no lo tienen, seedPUC se
   -- encargará y con el árbol completo.
   AND EXISTS (
        SELECT 1 FROM plan_cuenta p
         WHERE p.empresa_id = e.id AND p.codigo = '5')
 ORDER BY e.id, v.orden;
