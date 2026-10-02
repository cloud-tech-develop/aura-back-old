-- ── V186: activos fijos completos (Fase 3 del plan World Office) ──────────
--
-- Ficha completa (placa, serial, póliza, activo padre, fecha de inicio de la
-- depreciación, unidades de producción), mantenimientos, adiciones que suben
-- el costo y la vida útil, y los datos de la baja y la venta con su asiento.
-- Ver docs/PLAN_CATALOGO_ACTIVOS_CONTABILIDAD.md (Fase 3).
--
-- Idempotente. Espejo en Laravel: 2026_07_03_000186_activos_fijos_completos.php

-- ── 1. Ficha ─────────────────────────────────────────────────────────────
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS placa                     VARCHAR(40);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS serial                    VARCHAR(80);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS marca                     VARCHAR(80);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS modelo                    VARCHAR(80);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS responsable_tercero_id    BIGINT;
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS activo_padre_id           BIGINT;
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS aseguradora               VARCHAR(120);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS poliza_numero             VARCHAR(60);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS poliza_vence              DATE;
-- La depreciación empieza el mes de esta fecha (por defecto, la de adquisición).
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS fecha_inicio_depreciacion DATE;
-- Unidades de producción: vida total en unidades (horas, km, piezas).
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS unidades_estimadas        NUMERIC(18,2);
-- Adiciones y mejoras capitalizadas: el costo depreciable es valor_compra + esto.
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS valor_adiciones           NUMERIC(18,2) NOT NULL DEFAULT 0;

-- ── 2. Baja y venta ──────────────────────────────────────────────────────
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS fecha_retiro              DATE;
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS motivo_retiro             VARCHAR(300);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS valor_venta               NUMERIC(18,2);
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS comprador_tercero_id      BIGINT;
ALTER TABLE activo_fijo ADD COLUMN IF NOT EXISTS asiento_retiro_id         BIGINT;

-- ── 3. Depreciación por período: método y unidades del mes ───────────────
ALTER TABLE depreciacion_periodo ADD COLUMN IF NOT EXISTS metodo   VARCHAR(20);
ALTER TABLE depreciacion_periodo ADD COLUMN IF NOT EXISTS unidades NUMERIC(18,2);
-- Un activo se deprecia una vez por período. Si una base vieja ya tiene
-- duplicados no se crea (la migración no debe caerse por datos previos):
-- el servicio igual verifica antes de calcular.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM depreciacion_periodo
                    GROUP BY activo_id, periodo_id HAVING COUNT(*) > 1) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS ux_depreciacion_activo_periodo
            ON depreciacion_periodo (activo_id, periodo_id);
    END IF;
END $$;

-- ── 4. Mantenimientos ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS activo_fijo_mantenimiento (
    id            BIGSERIAL     PRIMARY KEY,
    empresa_id    INT           NOT NULL,
    activo_id     BIGINT        NOT NULL REFERENCES activo_fijo(id),
    fecha         DATE          NOT NULL,
    tipo          VARCHAR(20)   NOT NULL DEFAULT 'PREVENTIVO',  -- PREVENTIVO | CORRECTIVO
    descripcion   VARCHAR(300)  NOT NULL,
    costo         NUMERIC(18,2) NOT NULL DEFAULT 0,
    tercero_id    BIGINT,
    proximo       DATE,
    created_at    TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_af_mantenimiento_activo ON activo_fijo_mantenimiento (activo_id);

-- ── 5. Adiciones (mejoras que se capitalizan) ────────────────────────────
CREATE TABLE IF NOT EXISTS activo_fijo_adicion (
    id                       BIGSERIAL     PRIMARY KEY,
    empresa_id               INT           NOT NULL,
    activo_id                BIGINT        NOT NULL REFERENCES activo_fijo(id),
    fecha                    DATE          NOT NULL,
    descripcion              VARCHAR(300)  NOT NULL,
    valor                    NUMERIC(18,2) NOT NULL,
    meses_adicionales        INT           NOT NULL DEFAULT 0,
    cuenta_contrapartida_id  BIGINT        NOT NULL,
    tercero_id               BIGINT,
    asiento_id               BIGINT,
    created_at               TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_af_adicion_activo ON activo_fijo_adicion (activo_id);
