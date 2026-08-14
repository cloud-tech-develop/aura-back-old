-- ── V105: Fase 3.b — detalle de la liquidación por concepto ─────────────────
--
-- ESTO ES NÚCLEO, NO CAPA DE PROYECTOS. Lo necesita toda empresa:
--   · Sin él no hay desprendible de pago que mostrarle al empleado.
--   · Sin él no hay cómo responder un reclamo ("el sistema dice 1.234.567"
--     no es una respuesta).
--   · Sin él NO HAY NÓMINA ELECTRÓNICA: la DIAN exige cada devengado y
--     deducción en su etiqueta específica, no un total. Armar el payload de
--     la Fase 5 sin esto es un `if` gigante e inmantenible.
--
-- Las dimensiones de proyecto/frente se agregan DESPUÉS (Fase 4, V1xx) como
-- columnas nullable. Una empresa sin proyectos usa esta tabla igual: una fila
-- por concepto y ya.
--
-- Seguro sin tocar código: solo crea tablas nuevas.

CREATE TABLE IF NOT EXISTS nomina_detalle (
    id              BIGSERIAL     PRIMARY KEY,
    nomina_id       BIGINT        NOT NULL REFERENCES nomina(id) ON DELETE CASCADE,
    concepto_id     BIGINT        NOT NULL REFERENCES concepto_nomina(id),

    -- De dónde salió: si vino de una novedad, cuál.
    novedad_id      BIGINT        REFERENCES nomina_novedad(id),

    cantidad        NUMERIC(10,2),           -- horas, días, unidades
    base            NUMERIC(15,2) NOT NULL DEFAULT 0,
    porcentaje      NUMERIC(7,4),

    valor           NUMERIC(15,2) NOT NULL DEFAULT 0,
    valor_empleado  NUMERIC(15,2) NOT NULL DEFAULT 0,
    valor_empleador NUMERIC(15,2) NOT NULL DEFAULT 0,

    -- El desglose paso a paso del cálculo. Es la diferencia entre un número
    -- y una explicación.
    -- Formato sugerido:
    --   [{"paso":"Base","valor":1300000},
    --    {"paso":"Días trabajados","valor":30},
    --    {"paso":"Tarifa","valor":"4%"},
    --    {"paso":"Resultado","valor":52000}]
    traza           JSONB,

    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_nomina_detalle_nomina   ON nomina_detalle(nomina_id);
CREATE INDEX IF NOT EXISTS idx_nomina_detalle_concepto ON nomina_detalle(concepto_id);

COMMENT ON TABLE nomina_detalle IS
    'Desglose de la liquidación: una fila por concepto por nómina. Núcleo — '
    'lo usan todas las empresas. La Fase 4 le agrega dimensiones de proyecto '
    'como columnas nullable.';

COMMENT ON COLUMN nomina_detalle.traza IS
    'Desglose paso a paso del cálculo, para el desprendible y para responder '
    'reclamos. Snapshot: NO se recalcula.';


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTAS PARA EL MOTOR (Fase 0)
--
-- 1. Los campos agregados de `nomina` (total_devengado, deduccion_salud,
--    aporte_arl, provision_prima, ...) se MANTIENEN como denormalización de
--    lectura, pero pasan a CALCULARSE desde nomina_detalle. Deben cuadrar
--    exactamente: es un invariante que los tests tienen que verificar.
--
--        SUM(nomina_detalle.valor WHERE clase='DEVENGADO') = nomina.total_devengado
--
-- 2. `traza` es SNAPSHOT, no se regenera. Si mañana cambia la tarifa de salud,
--    la nómina de marzo debe seguir explicándose con la tarifa de marzo.
--    Mismo criterio que pila_cotizante.cod_eps y el XML de nomina_electronica:
--    los documentos generados guardan literales; los datos maestros guardan FK.
--
-- 3. `novedad_id` permite rastrear qué novedad produjo qué línea. En el ERP de
--    referencia esto es `nom_detalles_liquidaciones_novedades`.
-- ═══════════════════════════════════════════════════════════════════════════
