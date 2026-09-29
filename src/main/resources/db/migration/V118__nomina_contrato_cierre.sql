-- ── V118: Fase 2 (cierre) — la nómina cuelga del contrato ───────────────────
--
-- Cierra lo que V103 dejó abierto: pone contrato_id NOT NULL y reemplaza
-- uq_nomina_empleado_periodo por uq_nomina_contrato_periodo.
--
-- ╔═══════════════════════════════════════════════════════════════════════════╗
-- ║ ⚠️ PRECONDICIONES                                                         ║
-- ╠═══════════════════════════════════════════════════════════════════════════╣
-- ║ 1. V102 y V103 aplicadas.                                                 ║
-- ║                                                                           ║
-- ║ 2. CÓDIGO DESPLEGADO (ya escrito):                                        ║
-- ║      · NominaEntity.contrato                                              ║
-- ║      · NominaService.liquidarContrato(periodoId, contratoId, empresaId)   ║
-- ║      · liquidarPeriodoCompleto() itera contratos vigentes                 ║
-- ║      · EmpleadoServiceImpl crea el contrato inicial                       ║
-- ║                                                                           ║
-- ║ 3. Sin nóminas huérfanas:                                                 ║
-- ║      SELECT COUNT(*) FROM nomina WHERE contrato_id IS NULL;   -- = 0      ║
-- ║                                                                           ║
-- ║ 4. Todo empleado activo con contrato:                                     ║
-- ║      SELECT COUNT(*) FROM empleados e                                     ║
-- ║       WHERE e.activo = TRUE                                               ║
-- ║         AND NOT EXISTS (SELECT 1 FROM contrato_laboral c                  ║
-- ║                          WHERE c.empleado_id = e.id                       ║
-- ║                            AND c.deleted_at IS NULL);        -- = 0      ║
-- ╚═══════════════════════════════════════════════════════════════════════════╝
--
-- ESTA ES LA MIGRACIÓN QUE HABILITA EL MULTI-VÍNCULO. Mientras
-- uq_nomina_empleado_periodo siga viva, un empleado con dos contratos NO puede
-- tener dos nóminas en el mismo período, aunque contrato_id exista.

-- ── Guardarraíles ───────────────────────────────────────────────────────────
DO $$
DECLARE
    huerfanas INT;
    sin_contrato INT;
BEGIN
    SELECT COUNT(*) INTO huerfanas FROM nomina WHERE contrato_id IS NULL;
    IF huerfanas > 0 THEN
        RAISE EXCEPTION
            'V118 abortada: % nóminas sin contrato_id. Correr el backfill de V103 y verificar. Consulta: SELECT id, empleado_id, periodo_id FROM nomina WHERE contrato_id IS NULL;',
            huerfanas;
    END IF;

    SELECT COUNT(*) INTO sin_contrato
      FROM empleados e
     WHERE e.activo = TRUE
       AND NOT EXISTS (SELECT 1 FROM contrato_laboral c
                        WHERE c.empleado_id = e.id AND c.deleted_at IS NULL);
    IF sin_contrato > 0 THEN
        RAISE EXCEPTION
            'V118 abortada: % empleados activos sin contrato. No podrán liquidarse. Consulta: SELECT id, nombres, apellidos FROM empleados e WHERE e.activo AND NOT EXISTS (SELECT 1 FROM contrato_laboral c WHERE c.empleado_id = e.id AND c.deleted_at IS NULL);',
            sin_contrato;
    END IF;
END $$;

-- ── El cierre ───────────────────────────────────────────────────────────────
ALTER TABLE nomina
    ALTER COLUMN contrato_id SET NOT NULL;

ALTER TABLE nomina
    DROP CONSTRAINT IF EXISTS uq_nomina_empleado_periodo;

-- Una nómina por CONTRATO por período. Un empleado con dos contratos activos
-- genera dos nóminas — que es exactamente el punto.
ALTER TABLE nomina
    ADD CONSTRAINT uq_nomina_contrato_periodo UNIQUE (contrato_id, periodo_id);

COMMENT ON COLUMN nomina.empleado_id IS
    'DEPRECADO como eje de liquidación — usar contrato_id. Se conserva para '
    'consultas y reportes por persona. Derivable: contrato.empleado_id.';


-- ═══════════════════════════════════════════════════════════════════════════
-- DEUDA CONOCIDA QUE ESTO DESTAPA
--
-- Las sumas de provisiones en NominaJPARepository agrupan POR EMPLEADO:
--     sumProvisionPrima(empresaId, empleadoId)
--     sumProvisionCesantias(...), sumProvisionVacaciones(...), etc.
--
-- Con multi-vínculo ACTIVO eso es incorrecto: un empleado con dos contratos
-- vería sumadas las provisiones de ambos, y al pagar la prima de uno se
-- consumiría el pasivo del otro.
--
-- Deben pasar a agrupar por contrato_id. Va en la FASE 8 (prestaciones), que
-- es donde esas sumas se consumen de verdad.
--
-- Hoy no hace daño: nadie tiene dos contratos. Pero el día que el primer
-- cliente lo use, esto es un error de plata.
-- ═══════════════════════════════════════════════════════════════════════════
