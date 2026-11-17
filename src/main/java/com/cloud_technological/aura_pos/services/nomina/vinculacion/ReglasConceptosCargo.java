package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import java.util.Set;

/**
 * ¿Un concepto del catálogo se liquida a esta vinculación? (F6 de
 * docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md). Reglas puras, sin acceso a datos.
 *
 * <ul>
 *   <li>Los conceptos de ley siempre se liquidan: los manda la ley, no el cargo.</li>
 *   <li>Lo que la vinculación excluye no se liquida.</li>
 *   <li>Si el cargo no tiene plantilla de conceptos, no restringe (comportamiento
 *       anterior: todos los conceptos activos de la empresa).</li>
 *   <li>Si la tiene, un concepto propio de la empresa solo se liquida si está en ella.</li>
 * </ul>
 */
public final class ReglasConceptosCargo {

    private ReglasConceptosCargo() {
    }

    /**
     * @param codigo            clave interna del concepto
     * @param deLey             concepto de ley
     * @param conceptosDelCargo claves de la plantilla del cargo (vacío = sin plantilla)
     * @param excluidos         claves que la vinculación excluye
     */
    public static boolean aplica(String codigo, boolean deLey, Set<String> conceptosDelCargo, Set<String> excluidos) {
        if (deLey) return true;
        if (excluidos.contains(codigo)) return false;
        return conceptosDelCargo.isEmpty() || conceptosDelCargo.contains(codigo);
    }
}
