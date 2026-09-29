-- ── V103: Fase 2 (cierre) — la nómina se liquida contra un CONTRATO ─────────
--
-- Hoy `uq_nomina_empleado_periodo UNIQUE (empleado_id, periodo_id)` impone una
-- nómina por empleado por período. Eso es justo lo que impide el multi-vínculo:
-- si alguien tiene dos contratos, cada uno debe liquidarse por separado.
--
-- ╔═══════════════════════════════════════════════════════════════════════════╗
-- ║ ⛔ REQUIERE CAMBIO DE CÓDIGO ANTES (parcialmente)                         ║
-- ╠═══════════════════════════════════════════════════════════════════════════╣
-- ║ El ALTER que pone contrato_id NOT NULL y cambia la unicidad exige que     ║
-- ║ NominaEntity tenga `contrato` y que NominaServiceImpl.liquidar() lo setee.║
-- ║                                                                           ║
-- ║ Esta migración deja contrato_id NULLABLE a propósito: es segura de correr ║
-- ║ ya. El cierre (NOT NULL + cambio de unicidad) está al final, comentado.   ║
-- ╚═══════════════════════════════════════════════════════════════════════════╝

ALTER TABLE nomina
    ADD COLUMN IF NOT EXISTS contrato_id BIGINT REFERENCES contrato_laboral(id);

CREATE INDEX IF NOT EXISTS idx_nomina_contrato ON nomina(contrato_id);

COMMENT ON COLUMN nomina.contrato_id IS
    'Contrato liquidado. Reemplaza a empleado_id como eje: una persona con dos '
    'contratos activos genera dos nóminas por período.';

-- Backfill: nómina está vacía, pero por si hay datos de prueba.
-- Toma el contrato principal activo del empleado.
UPDATE nomina n
   SET contrato_id = c.id
  FROM contrato_laboral c
 WHERE n.contrato_id IS NULL
   AND c.empleado_id = n.empleado_id
   AND c.deleted_at IS NULL
   AND c.es_principal = TRUE
   AND (SELECT COUNT(*) FROM contrato_laboral c2
         WHERE c2.empleado_id = n.empleado_id
           AND c2.deleted_at IS NULL
           AND c2.es_principal = TRUE) = 1;


-- ═══════════════════════════════════════════════════════════════════════════
-- CIERRE — descomentar y correr como V10x SOLO cuando:
--
--   1. NominaEntity tenga:
--        @ManyToOne(fetch = FetchType.LAZY)
--        @JoinColumn(name = "contrato_id", nullable = false)
--        private ContratoLaboralEntity contrato;
--
--   2. NominaServiceImpl.liquidar(periodoId, empleadoId, empresaId) cambie a
--      liquidar(periodoId, contratoId, empresaId) — o resuelva el contrato
--      activo del empleado y lo setee.
--
--   3. liquidarPeriodoCompleto() itere contratos activos, no empleados.
--
--   4. Verificar que no queden huérfanas:
--        SELECT COUNT(*) FROM nomina WHERE contrato_id IS NULL;
--
-- -- ALTER TABLE nomina ALTER COLUMN contrato_id SET NOT NULL;
-- -- ALTER TABLE nomina DROP CONSTRAINT uq_nomina_empleado_periodo;
-- -- ALTER TABLE nomina ADD CONSTRAINT uq_nomina_contrato_periodo
-- --     UNIQUE (contrato_id, periodo_id);
--
-- OJO: mientras uq_nomina_empleado_periodo siga vivo, el multi-vínculo NO
-- funciona aunque contrato_id exista — la restricción vieja lo bloquea.
-- ═══════════════════════════════════════════════════════════════════════════
