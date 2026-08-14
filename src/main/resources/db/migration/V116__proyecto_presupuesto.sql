-- ── V116: Fase 4.c/4.d — presupuesto por proyecto y compromiso ──────────────
--
-- CAPA OPCIONAL, pero GENÉRICA: "¿voy sobre o bajo presupuesto?" la preguntan
-- una constructora, una agencia y un taller por igual.
--
-- PROBLEMA: `proyecto` tiene codigo, nombre, cliente_id, fechas, centro_costo_id,
-- ubicacion... y NINGÚN campo de valor. Se puede acumular lo gastado (vía
-- asiento_detalle.proyecto_id) pero no hay contra qué compararlo.
-- `centro_costo.presupuesto_asignado` (V49) existe pero es por centro de costo,
-- sin desagregación y sin relación con el proyecto.
--
-- Seguro sin tocar código: crea tablas y agrega columnas nullable.

CREATE TABLE IF NOT EXISTS proyecto_presupuesto (
    id                 BIGSERIAL     PRIMARY KEY,
    empresa_id         INT           NOT NULL REFERENCES empresa(id),
    proyecto_id        BIGINT        NOT NULL REFERENCES proyecto(id),
    frente_id          BIGINT        REFERENCES proyecto_frente(id),

    capitulo           VARCHAR(100),
    codigo             VARCHAR(30)   NOT NULL,
    descripcion        VARCHAR(300)  NOT NULL,

    -- Alinea con nomina_detalle (MANO_OBRA), compra y gasto.
    tipo_costo         VARCHAR(20)   NOT NULL,

    -- Para casar presupuesto vs. ejecutado por cuenta del PUC.
    cuenta_contable_id BIGINT,

    valor              NUMERIC(15,2) NOT NULL DEFAULT 0,

    -- ── PAC: programación mensual ───────────────────────────────────────────
    -- La mejor idea del ERP de referencia y directamente aplicable.
    -- No solo CUÁNTO se va a gastar, sino CUÁNDO. Es el flujo de caja de la
    -- obra: permite anticipar el bache del mes 4 antes de llegar al mes 4.
    pac01 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac02 NUMERIC(15,2) NOT NULL DEFAULT 0,
    pac03 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac04 NUMERIC(15,2) NOT NULL DEFAULT 0,
    pac05 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac06 NUMERIC(15,2) NOT NULL DEFAULT 0,
    pac07 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac08 NUMERIC(15,2) NOT NULL DEFAULT 0,
    pac09 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac10 NUMERIC(15,2) NOT NULL DEFAULT 0,
    pac11 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac12 NUMERIC(15,2) NOT NULL DEFAULT 0,

    -- Reprogramar sin perder la línea base: comparar contra el original es la
    -- mitad del valor.
    version            INT           NOT NULL DEFAULT 1,
    activo             BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by         BIGINT,

    CONSTRAINT chk_pp_tipo CHECK (tipo_costo IN
        ('MANO_OBRA', 'MATERIAL', 'EQUIPO', 'SUBCONTRATO', 'INDIRECTO', 'OTRO')),
    CONSTRAINT uq_pp UNIQUE (proyecto_id, codigo, version)
);

CREATE INDEX IF NOT EXISTS idx_pp_proyecto ON proyecto_presupuesto(proyecto_id) WHERE activo = TRUE;


-- ── 4.d — Compromiso vs. ejecutado ──────────────────────────────────────────
-- Otro hueco de la V92: le puso proyecto_id/frente_id a `compra` y `gasto`,
-- pero NO a `orden_compra`. Una orden emitida no se puede atribuir a un proyecto.
--
-- Sin esto la obra se ve en verde hasta que llegan las facturas todas juntas.
-- Sale casi gratis: orden_compra ya tiene estados y `total`.
ALTER TABLE orden_compra
    ADD COLUMN IF NOT EXISTS proyecto_id         BIGINT,
    ADD COLUMN IF NOT EXISTS frente_id           BIGINT,
    ADD COLUMN IF NOT EXISTS presupuesto_item_id BIGINT REFERENCES proyecto_presupuesto(id);

CREATE INDEX IF NOT EXISTS idx_orden_compra_proyecto
    ON orden_compra(proyecto_id) WHERE proyecto_id IS NOT NULL;


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTAS
--
-- MAPEO DE ESTADOS (los de orden_compra YA EXISTEN):
--
--   BORRADOR           → nada, no afecta
--   ENVIADA, CONFIRMADA→ COMPROMETIDO — reserva presupuesto
--   RECIBIDA_PARCIAL   → comprometido el saldo; ejecutado lo recibido
--   CERRADA            → EJECUTADO (ya hay `compra`); libera el compromiso
--   ANULADA            → libera el compromiso
--
-- VISTA DE CONTROL:
--   Presupuesto − Comprometido − Ejecutado = Disponible
--
-- EJECUTADO: NO crear tabla de saldos todavía. Se resuelve con consulta sobre
-- asiento_detalle filtrando por proyecto_id, agrupada por cuenta/tipo_costo.
-- El ERP materializa saldos (pre_saldos_*) porque mueve volúmenes de sector
-- público; a este tamaño es optimización prematura. Si la consulta se pone
-- lenta, AHÍ se materializa.
--
-- LO QUE NO SE TOMA DEL ERP:
--   La cadena formal CDP → RP → obligación → pago, pre_cpcs, fuentes de
--   financiamiento, vigencias y cierres presupuestales. Eso es obligación
--   legal del sector público colombiano. TOMAR EL CONCEPTO DE COMPROMISO,
--   NO EL APARATO JURÍDICO.
--
--   Bloquear una compra por falta de presupuesto debe ser una ADVERTENCIA
--   CONFIGURABLE, no un impedimento.
--
-- FUERA DE ALCANCE (esperar a que el segmento lo justifique):
--   · APU (análisis de precios unitarios) — el ERP tampoco lo tiene
--   · Avance de obra / % ejecución física — sin esto, presupuesto vs. gasto
--     miente: gastaste el 60% pero ¿construiste el 60% o el 30%?
--   · Actas de obra / cortes
--   · Subcontratos por frente con su propio corte
--
-- COBERTURA BARATA: si los clientes ya presupuestan en Excel, un importador a
-- esta tabla vale más que exigirles cambiar su forma de trabajar.
-- ═══════════════════════════════════════════════════════════════════════════
