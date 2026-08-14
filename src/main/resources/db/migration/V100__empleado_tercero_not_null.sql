-- ── V100: Fase 1.b (cierre) — `empleados.tercero_id` obligatorio ────────────
--
-- ╔═══════════════════════════════════════════════════════════════════════════╗
-- ║ ⚠️ PRECONDICIONES — verificar las DOS antes de correr                     ║
-- ╠═══════════════════════════════════════════════════════════════════════════╣
-- ║                                                                           ║
-- ║ 1. CÓDIGO DESPLEGADO ✔ (ya escrito, falta desplegarlo)                    ║
-- ║      · EmpleadoEntity.tercero          → @ManyToOne a TerceroEntity       ║
-- ║      · EmpleadoServiceImpl.crear()     → resolverTercero(): usa el        ║
-- ║        terceroId del DTO, o busca por documento, o crea el tercero.       ║
-- ║                                                                           ║
-- ║    Si esta migración corre SIN ese código desplegado, el primer INSERT    ║
-- ║    de empleado no manda tercero_id y falla por NOT NULL.                  ║
-- ║                                                                           ║
-- ║ 2. RECONCILIACIÓN DE V99 COMPLETA                                         ║
-- ║      SELECT COUNT(*) FROM empleados WHERE tercero_id IS NULL;  -- = 0     ║
-- ║                                                                           ║
-- ║ ORDEN:  V99 → reconciliar → desplegar código → V100                       ║
-- ╚═══════════════════════════════════════════════════════════════════════════╝
--
-- Verificación previa (debe dar 0 en TODOS los ambientes):
--
--     SELECT COUNT(*) FROM empleados WHERE tercero_id IS NULL;
--
-- Si da > 0, esta migración FALLA y deja Flyway en estado failed, lo que
-- bloquea el arranque. Resolver los pendientes del bloque "PASOS MANUALES"
-- de V99 primero.
--
-- Guardarraíl: si quedan huérfanos, aborta con mensaje claro en vez de
-- reventar con un error de constraint sin contexto.

DO $$
DECLARE
    huerfanos INT;
BEGIN
    SELECT COUNT(*) INTO huerfanos FROM empleados WHERE tercero_id IS NULL;
    IF huerfanos > 0 THEN
        RAISE EXCEPTION
            'V100 abortada: % empleados sin tercero_id. Completar la reconciliación de V99 antes de aplicar. Consulta: SELECT id, nombres, apellidos, numero_documento FROM empleados WHERE tercero_id IS NULL;',
            huerfanos;
    END IF;
END $$;

ALTER TABLE empleados
    ALTER COLUMN tercero_id SET NOT NULL;

-- Una persona = un registro de empleado por empresa.
-- Si un cliente necesita multi-vínculo simultáneo, eso se modela en
-- contrato_laboral (V102), NO duplicando el empleado.
ALTER TABLE empleados
    ADD CONSTRAINT uq_empleado_tercero UNIQUE (empresa_id, tercero_id);


-- ── Columnas deprecadas (NO borrar todavía) ─────────────────────────────────
-- `empleados.nombres`, `apellidos`, `tipo_documento`, `numero_documento`,
-- `banco`, `numero_cuenta`, `tipo_cuenta` quedan duplicadas contra `tercero`.
--
-- Se borran en una migración futura, cuando ningún código las lea. Orden:
--   1. Esta migración (FK obligatoria)                        ← estás aquí
--   2. Código: EmpleadoServiceImpl y NominaServiceImpl leen de tercero
--      (ojo: NominaServiceImpl:133 y :814 hacen getEmpleado().getBanco())
--   3. Migración futura: DROP COLUMN
COMMENT ON COLUMN empleados.nombres IS
    'DEPRECADO — leer de tercero.nombre1/nombre2. Se elimina cuando el código migre.';
COMMENT ON COLUMN empleados.apellidos IS
    'DEPRECADO — leer de tercero.apellido1/apellido2.';
COMMENT ON COLUMN empleados.numero_documento IS
    'DEPRECADO — leer de tercero.numero_documento.';
COMMENT ON COLUMN empleados.banco IS
    'DEPRECADO — leer de tercero.banco (FK en V101).';
