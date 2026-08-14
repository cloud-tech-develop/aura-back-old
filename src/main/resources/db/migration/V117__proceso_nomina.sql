-- ── V117: Fase 7 — procesos asíncronos con progreso ─────────────────────────
--
-- PROBLEMA: `liquidarPeriodoCompleto()` corre síncrono dentro del request HTTP.
-- Con 30 empleados va bien; con 500 y provisiones, timeout. Lo mismo aplicará
-- a los envíos DIAN (Fase 5) y a la generación de PILA (Fase 6).
--
-- Equivale a `nom_procesos_logs` + ComProgressEvent del ERP de referencia.
--
-- TRANSVERSAL: se puede adelantar en cualquier momento si el volumen aprieta.
--
-- Seguro sin tocar código: solo crea tablas nuevas.

CREATE TABLE IF NOT EXISTS proceso_nomina (
    id           BIGSERIAL     PRIMARY KEY,
    empresa_id   INT           NOT NULL REFERENCES empresa(id),

    tipo         VARCHAR(40)   NOT NULL,
    referencia_id BIGINT,                     -- periodo_id, nomina_id, etc.

    estado       VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    progreso     INT           NOT NULL DEFAULT 0,   -- 0..100
    mensaje      VARCHAR(300),

    total_items     INT        NOT NULL DEFAULT 0,
    items_ok        INT        NOT NULL DEFAULT 0,
    items_error     INT        NOT NULL DEFAULT 0,

    -- Errores por item, sin abortar el lote.
    errores      JSONB,

    -- Quién y cuándo. El ERP registra procesa/reversa por separado; aquí igual.
    usuario_id   BIGINT,
    iniciado_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    finalizado_at TIMESTAMP,

    -- Reversa
    reversado_por BIGINT,
    reversado_at  TIMESTAMP,

    CONSTRAINT chk_proc_estado CHECK (estado IN
        ('PENDIENTE', 'EN_PROCESO', 'COMPLETADO', 'COMPLETADO_CON_ERRORES',
         'FALLIDO', 'REVERSADO')),
    CONSTRAINT chk_proc_progreso CHECK (progreso BETWEEN 0 AND 100),
    CONSTRAINT chk_proc_tipo CHECK (tipo IN (
        'LIQUIDACION_PERIODO',
        'LIQUIDACION_PRESTACIONES',
        'NOMINA_ELECTRONICA',
        'NOMINA_ELECTRONICA_AJUSTE',
        'PILA_GENERACION',
        'CONTABILIZACION',
        'IMPORTACION'
    ))
);

CREATE INDEX IF NOT EXISTS idx_proceso_empresa ON proceso_nomina(empresa_id, tipo);
CREATE INDEX IF NOT EXISTS idx_proceso_activo
    ON proceso_nomina(estado) WHERE estado IN ('PENDIENTE', 'EN_PROCESO');


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTAS
--
-- FLUJO:
--   1. El endpoint crea el proceso en PENDIENTE y devuelve su id (202 Accepted)
--   2. El job lo toma → EN_PROCESO, va actualizando progreso y mensaje
--   3. El front hace polling a GET /proceso/{id}
--   4. Al terminar → COMPLETADO / COMPLETADO_CON_ERRORES / FALLIDO
--
-- COMPLETADO_CON_ERRORES existe a propósito: en un lote de 500 empleados, que
-- 3 fallen no debe abortar los 497 buenos. Los errores van a `errores` (JSONB)
-- con el detalle por item, y el usuario decide qué hacer con esos 3.
--
-- REVERSA: el ERP permite reversar un proceso confirmado y registra quién lo
-- hizo. Aquí `reversado_por`/`reversado_at`.
--
-- ⚠️ NO usar @Async a secas: si el proceso se reinicia, los jobs en vuelo se
--    pierden y quedan filas en EN_PROCESO para siempre. Al arrancar, marcar
--    como FALLIDO los que quedaron colgados, o usar una cola con reintentos.
--
-- ⚠️ La transaccionalidad: el job NO debe correr todo en una transacción de
--    500 empleados. Commit por empleado (o por lotes chicos), para que un
--    fallo tardío no bote el trabajo bueno.
-- ═══════════════════════════════════════════════════════════════════════════
