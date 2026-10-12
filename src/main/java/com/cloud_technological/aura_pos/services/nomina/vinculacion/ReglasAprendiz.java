package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import java.util.Set;

import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;

/**
 * Reglas del contrato de aprendizaje SENA (formación tradicional) según la Ley 2466
 * de 2025 y el Decreto 223 de 2026.
 *
 * <ul>
 *   <li><b>Etapa lectiva</b>: apoyo del 75 % del SMMLV. La empresa paga la salud
 *       completa (12,5 % sobre un SMMLV) y la ARL; al aprendiz no se le descuenta
 *       salud ni pensión, no cotiza pensión ni causa prestaciones.</li>
 *   <li><b>Etapa práctica</b>: apoyo del 100 % del SMMLV. Se le descuenta 4 % de
 *       salud y 4 % de pensión, la empresa pone su parte de salud y pensión y la ARL,
 *       y causa prestaciones sociales (prima, cesantías, intereses y vacaciones).</li>
 * </ul>
 *
 * <p>En ninguna de las dos etapas lleva parafiscales, fondo de solidaridad ni
 * retención en la fuente (su apoyo no supera los topes).
 */
public final class ReglasAprendiz {

    public static final String APRENDIZAJE = "APRENDIZAJE";
    public static final String PRACTICA = "PRACTICA";

    /** Tarifa de salud que asume la empresa en etapa lectiva (sobre un SMMLV). */
    public static final String PCT_SALUD_LECTIVA = "12.5";

    /** Conceptos que nunca aplican al aprendiz. */
    private static final Set<String> NUNCA = Set.of(
            "APO_CCF", "APO_ICBF", "APO_SENA", "DED_FONDO_SOLIDARIDAD");

    /** Conceptos que además no aplican en la etapa lectiva. */
    private static final Set<String> NO_EN_LECTIVA = Set.of(
            "DED_SALUD", "DED_PENSION", "APO_PENSION",
            "PRO_PRIMA", "PRO_CESANTIAS", "PRO_INT_CES", "PRO_VACAC");

    private ReglasAprendiz() {
    }

    /** El contrato es de aprendizaje SENA. */
    public static boolean esAprendiz(ContratoLaboralEntity contrato) {
        return contrato != null && APRENDIZAJE.equals(contrato.getTipoContrato());
    }

    /** Aprendiz en etapa práctica (la fase sin definir se trata como lectiva). */
    public static boolean enPractica(ContratoLaboralEntity contrato) {
        return esAprendiz(contrato) && PRACTICA.equals(contrato.getFase());
    }

    /** Aprendiz en etapa lectiva. */
    public static boolean enLectiva(ContratoLaboralEntity contrato) {
        return esAprendiz(contrato) && !PRACTICA.equals(contrato.getFase());
    }

    /**
     * El concepto del catálogo no se liquida para este aprendiz.
     *
     * @param contrato contrato de aprendizaje
     * @param codigo   código del concepto
     */
    public static boolean conceptoNoAplica(ContratoLaboralEntity contrato, String codigo) {
        if (!esAprendiz(contrato)) return false;
        return NUNCA.contains(codigo) || (enLectiva(contrato) && NO_EN_LECTIVA.contains(codigo));
    }

    /**
     * El contrato causa prestaciones sociales: todos los laborales, salvo servicios y
     * el aprendiz en etapa lectiva.
     */
    public static boolean causaPrestaciones(ContratoLaboralEntity contrato) {
        if (contrato == null || "PRESTACION_SERVICIOS".equals(contrato.getTipoContrato())) return false;
        return !enLectiva(contrato);
    }
}
