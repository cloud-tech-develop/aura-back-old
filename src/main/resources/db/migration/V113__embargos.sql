-- ── V113: Fase 9 — embargos y descuentos judiciales ─────────────────────────
--
-- Hoy: una novedad plana de tipo 'EMBARGO' con valor_unitario. No hay
-- expediente, ni juzgado, ni prelación, ni saldo que se consuma.
--
-- Seguro sin tocar código: solo crea tablas nuevas.

CREATE TABLE IF NOT EXISTS embargo (
    id            BIGSERIAL     PRIMARY KEY,
    empresa_id    INT           NOT NULL REFERENCES empresa(id),
    contrato_id   BIGINT        NOT NULL REFERENCES contrato_laboral(id),

    expediente    VARCHAR(60)   NOT NULL,
    tipo          VARCHAR(30)   NOT NULL,

    -- Orden de aplicación cuando concurren varios. Menor = primero.
    -- Alimentos SIEMPRE va primero (prioridad 1).
    prioridad     INT           NOT NULL DEFAULT 1,

    -- Al juzgado se le GIRA: por eso es tercero, no texto.
    juzgado_id    BIGINT        REFERENCES tercero(id),
    demandante_id BIGINT        REFERENCES tercero(id),

    -- O valor_total (se descuenta hasta agotarlo) o porcentaje (indefinido).
    valor_total   NUMERIC(15,2),
    porcentaje    NUMERIC(5,2),
    saldo         NUMERIC(15,2) NOT NULL DEFAULT 0,

    fecha_inicio  DATE          NOT NULL,
    fecha_fin     DATE,
    estado        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
    observacion   VARCHAR(500),

    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP,

    CONSTRAINT chk_embargo_tipo CHECK (tipo IN
        ('ALIMENTOS', 'COOPERATIVA', 'JUDICIAL_ORDINARIO', 'FISCAL')),
    CONSTRAINT chk_embargo_estado CHECK (estado IN
        ('ACTIVO', 'SUSPENDIDO', 'TERMINADO')),
    -- O monto o porcentaje, no ambos, no ninguno.
    CONSTRAINT chk_embargo_monto CHECK (
        (valor_total IS NOT NULL AND porcentaje IS NULL) OR
        (valor_total IS NULL AND porcentaje IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_embargo_contrato
    ON embargo(contrato_id, estado) WHERE estado = 'ACTIVO';


-- ── Rastro de lo descontado en cada nómina ──────────────────────────────────
CREATE TABLE IF NOT EXISTS embargo_descuento (
    id            BIGSERIAL     PRIMARY KEY,
    embargo_id    BIGINT        NOT NULL REFERENCES embargo(id),
    nomina_id     BIGINT        NOT NULL REFERENCES nomina(id) ON DELETE CASCADE,
    valor         NUMERIC(15,2) NOT NULL,
    saldo_antes   NUMERIC(15,2) NOT NULL,
    saldo_despues NUMERIC(15,2) NOT NULL,
    -- Si no cupo por el límite legal, cuánto quedó sin descontar.
    valor_diferido NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_embargo_nomina UNIQUE (embargo_id, nomina_id)
);

CREATE INDEX IF NOT EXISTS idx_embargo_desc_nomina ON embargo_descuento(nomina_id);


-- ── Conceptos ───────────────────────────────────────────────────────────────
INSERT INTO concepto_nomina
    (empresa_id, codigo, nombre, clase, constituye_ibc, base, vigente_desde, orden, codigo_dian)
VALUES
    (NULL, 'EMB_ALIMENTOS', 'Embargo por alimentos',  'DEDUCCION', FALSE, 'MANUAL', '2026-01-01', 600, 'Libranza'),
    (NULL, 'EMB_JUDICIAL',  'Embargo judicial',       'DEDUCCION', FALSE, 'MANUAL', '2026-01-01', 610, 'Libranza'),
    (NULL, 'EMB_COOP',      'Embargo cooperativa',    'DEDUCCION', FALSE, 'MANUAL', '2026-01-01', 620, 'Libranza')
ON CONFLICT (empresa_id, codigo, vigente_desde) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTAS PARA EL MOTOR — la PRELACIÓN es lo difícil, no la tabla
--
-- LÍMITES LEGALES (CST art. 154-156):
--
--   · Regla general: el salario es INEMBARGABLE hasta 1 SMMLV.
--     Del excedente sobre 1 SMMLV, solo se puede embargar la QUINTA PARTE (20%).
--
--   · EXCEPCIÓN — alimentos y cooperativas: hasta el 50% del salario TOTAL,
--     sin el piso del SMMLV.
--
--   · Los embargos por ALIMENTOS tienen PRELACIÓN sobre todos los demás.
--
-- ALGORITMO con varios embargos concurrentes:
--
--   1. Calcular el límite general:  (neto - 1 SMMLV) * 20%
--   2. Calcular el límite alimentos: neto * 50%
--   3. Ordenar embargos activos por prioridad (alimentos primero)
--   4. Para cada uno, en orden:
--        · Determinar su cupo (según tipo: límite alimentos o general)
--        · Descontar MIN(valor_solicitado, cupo_restante, saldo)
--        · Lo que no cupo → valor_diferido, se intenta el mes siguiente
--        · Actualizar saldo
--   5. Registrar cada descuento en embargo_descuento
--
-- ⚠️ Un embargo de alimentos + uno ordinario NO suman 50% + 20%: el ordinario
--    solo puede tomar del remanente tras el de alimentos, y sin exceder su
--    propio límite.
--
-- ⚠️ Los embargos se descuentan del NETO (después de salud y pensión), no del
--    devengado.
--
-- TESTS OBLIGATORIOS:
--   · Un empleado con salario mínimo y un embargo ordinario → descuento CERO
--   · Alimentos + ordinario concurrentes
--   · Embargo con saldo menor al cupo → se termina (estado=TERMINADO)
--   · Embargo que no cabe → valor_diferido
-- ═══════════════════════════════════════════════════════════════════════════
