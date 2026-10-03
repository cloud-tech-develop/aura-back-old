-- ── V185: catálogo unificado (Fase 2 del plan World Office) ────────────────
--
-- Todo lo que la empresa compra vive en el mismo catálogo y se clasifica:
-- PRODUCTO, SERVICIO, GASTO, DOTACION, ACTIVO_FIJO, INTANGIBLE o DIFERIDO.
-- La clasificación decide qué hace la compra con la línea: solo PRODUCTO
-- mueve inventario; ACTIVO_FIJO/INTANGIBLE crean la ficha del activo y
-- DIFERIDO crea el diferido con sus cuotas. Ver docs/PLAN_CATALOGO_ACTIVOS_CONTABILIDAD.md.
--
-- Idempotente. Espejo en Laravel: 2026_07_03_000185_catalogo_unificado.php

-- ── 1. Clasificación del ítem ─────────────────────────────────────────────
ALTER TABLE producto ADD COLUMN IF NOT EXISTS clasificacion VARCHAR(20) NOT NULL DEFAULT 'PRODUCTO';

-- Un servicio ya existente no debe seguir subiendo stock al comprarlo. Solo
-- los que no tienen existencias: uno con stock queda PRODUCTO para no dejar
-- ese inventario sin quién lo mueva (el usuario lo reclasifica al sacarlo).
UPDATE producto p SET clasificacion = 'SERVICIO',
       maneja_inventario = FALSE, maneja_lotes = FALSE, maneja_serial = FALSE
WHERE p.tipo_producto = 'SERVICIO' AND p.clasificacion = 'PRODUCTO'
  AND NOT EXISTS (SELECT 1 FROM inventario i WHERE i.producto_id = p.id AND i.stock_actual <> 0);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_producto_clasificacion') THEN
        ALTER TABLE producto ADD CONSTRAINT ck_producto_clasificacion CHECK (clasificacion IN
            ('PRODUCTO','SERVICIO','GASTO','DOTACION','ACTIVO_FIJO','INTANGIBLE','DIFERIDO'));
    END IF;
END $$;

-- La línea de compra guarda la clasificación con que se registró: si el
-- producto cambia después, anular o editar la compra deshace lo que la línea
-- hizo entonces. NULL (compras previas) = PRODUCTO, que siempre movió stock.
ALTER TABLE compra_detalle ADD COLUMN IF NOT EXISTS clasificacion VARCHAR(20);

-- "Llegaron 4 pacas y 2 cervezas" en una sola línea: las unidades sueltas que
-- acompañan a la presentación, en unidad base.
ALTER TABLE compra_detalle ADD COLUMN IF NOT EXISTS cantidad_suelta NUMERIC(38,6);

-- ── 2. Datos de la plantilla contable para activos y diferidos ───────────
ALTER TABLE categoria_contable_producto ADD COLUMN IF NOT EXISTS cuenta_depreciacion_id       BIGINT;
ALTER TABLE categoria_contable_producto ADD COLUMN IF NOT EXISTS cuenta_gasto_depreciacion_id BIGINT;
ALTER TABLE categoria_contable_producto ADD COLUMN IF NOT EXISTS vida_util_meses              INTEGER;
ALTER TABLE categoria_contable_producto ADD COLUMN IF NOT EXISTS meses_diferido               INTEGER;

-- ── 3. La ficha del activo sabe de qué compra salió ──────────────────────
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS compra_id         BIGINT;
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS compra_detalle_id BIGINT;
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS producto_id       BIGINT;
CREATE INDEX IF NOT EXISTS ix_activo_fijo_compra ON activo_fijo (compra_id) WHERE compra_id IS NOT NULL;

-- ── 4. Diferido generalizado (gasto o compra) ────────────────────────────
CREATE TABLE IF NOT EXISTS diferido (
    id                 BIGSERIAL     PRIMARY KEY,
    empresa_id         INT           NOT NULL,
    origen_tipo        VARCHAR(20)   NOT NULL,              -- COMPRA
    origen_id          BIGINT        NOT NULL,
    compra_detalle_id  BIGINT,
    producto_id        BIGINT,
    descripcion        VARCHAR(200)  NOT NULL,
    monto              NUMERIC(18,2) NOT NULL,
    meses              INT           NOT NULL,
    fecha_inicio       DATE          NOT NULL,
    cuenta_gasto_id    BIGINT,
    cuenta_diferido_id BIGINT,
    tercero_id         BIGINT,
    centro_costo_id    BIGINT,
    estado             VARCHAR(20)   NOT NULL DEFAULT 'VIGENTE', -- VIGENTE | TERMINADO | ANULADO
    created_at         TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_diferido_origen ON diferido (empresa_id, origen_tipo, origen_id);

ALTER TABLE diferido_amortizacion ADD COLUMN IF NOT EXISTS diferido_id BIGINT;
ALTER TABLE diferido_amortizacion ALTER COLUMN gasto_id DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_diferido_amortizacion_periodo
    ON diferido_amortizacion (diferido_id, periodo) WHERE diferido_id IS NOT NULL;

-- ── 5. Reorden por bodega ────────────────────────────────────────────────
-- Punto de reorden: con este saldo o menos hay que pedir. Máximo: hasta dónde
-- llenar. El sugerido de compra = máximo − stock.
ALTER TABLE inventario ADD COLUMN IF NOT EXISTS stock_maximo  NUMERIC(38,6);
ALTER TABLE inventario ADD COLUMN IF NOT EXISTS punto_reorden NUMERIC(38,6);

-- ── 6. Cuentas del PUC que usan las clasificaciones nuevas ───────────────
INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT c.empresa_id, '16', 'Intangibles', 'ACTIVO', 'DEBITO', 2, c.id, TRUE, FALSE, now()
FROM plan_cuenta c WHERE c.codigo = '1'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = c.empresa_id AND x.codigo = '16');

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT p.empresa_id, v.codigo, v.nombre, v.tipo, v.naturaleza, 3, p.id, TRUE, TRUE, now()
FROM (VALUES ('1524', 'Equipo de Oficina',                        'ACTIVO', 'DEBITO',  '15'),
             ('1528', 'Equipo de Computacion y Comunicacion',     'ACTIVO', 'DEBITO',  '15'),
             ('1635', 'Licencias',                                'ACTIVO', 'DEBITO',  '16'),
             ('1698', 'Amortizacion Acumulada',                   'ACTIVO', 'CREDITO', '16'),
             ('4245', 'Utilidad en Venta de Propiedades Planta y Equipo', 'INGRESO', 'CREDITO', '42'),
             ('5310', 'Perdida en Venta y Retiro de Bienes',      'GASTO',  'DEBITO',  '53')
     ) AS v(codigo, nombre, tipo, naturaleza, padre)
JOIN plan_cuenta p ON p.codigo = v.padre
WHERE NOT EXISTS (SELECT 1 FROM plan_cuenta x
                  WHERE x.empresa_id = p.empresa_id AND x.codigo = v.codigo);

INSERT INTO plan_cuenta (empresa_id, codigo, nombre, tipo, naturaleza, nivel, padre_id, activa, auxiliar, created_at)
SELECT p.empresa_id, '510551', 'Dotacion y Suministro a Trabajadores', 'GASTO', 'DEBITO', 4, p.id, TRUE, TRUE, now()
FROM plan_cuenta p WHERE p.codigo = '5105'
  AND NOT EXISTS (SELECT 1 FROM plan_cuenta x WHERE x.empresa_id = p.empresa_id AND x.codigo = '510551');
