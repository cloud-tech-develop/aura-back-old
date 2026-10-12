package com.cloud_technological.aura_pos.services.nomina.catalogos;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Valores con los que arranca cada catálogo de nómina en una empresa que aún no lo
 * tiene. Son los usuales en Colombia; la empresa los edita, desactiva o amplía.
 */
public final class CatalogoNominaSemilla {

    /** Un elemento inicial del catálogo. */
    public record Elemento(String codigo, String nombre, Map<String, Object> atributos) {
    }

    private CatalogoNominaSemilla() {
    }

    /**
     * Elementos iniciales del tipo.
     *
     * @param tipo catálogo
     * @return lista en el orden en que se muestran
     */
    public static List<Elemento> de(TipoCatalogoNomina tipo) {
        return switch (tipo) {
            case GRUPO_NOMINA -> List.of(
                    e("ADM", "Administrativos"),
                    e("OPE", "Operativos"));
            case NIVEL_CARGO -> List.of(
                    e("DIR", "Directivo"),
                    e("PRO", "Profesional"),
                    e("TEC", "Técnico"),
                    e("ASI", "Asistencial"),
                    e("OPE", "Operativo"));
            case TIPO_CARGO -> List.of(
                    e("PLA", "Planta"),
                    e("TEM", "Temporal"),
                    e("PRY", "Por proyecto"));
            case TIPO_EMBARGO -> List.of(
                    e("ALI", "Alimentos", Map.of("prioridad", 1, "limitePorcentaje", new BigDecimal("50"),
                            "depositoJudicial", true)),
                    e("EJE", "Ejecutivo o civil", Map.of("prioridad", 2, "limitePorcentaje", new BigDecimal("20"),
                            "depositoJudicial", true)),
                    e("COO", "Cooperativa", Map.of("prioridad", 3, "limitePorcentaje", new BigDecimal("50"),
                            "depositoJudicial", false)));
            case MOTIVO_RETIRO -> List.of(
                    e("REN", "Renuncia voluntaria", Map.of("indemniza", false)),
                    e("DJC", "Despido con justa causa", Map.of("indemniza", false)),
                    e("DSJ", "Despido sin justa causa", Map.of("indemniza", true)),
                    e("TCF", "Terminación de contrato a término fijo", Map.of("indemniza", false)),
                    e("TOL", "Terminación de obra o labor", Map.of("indemniza", false)),
                    e("MAC", "Mutuo acuerdo", Map.of("indemniza", false)),
                    e("PEN", "Reconocimiento de pensión", Map.of("indemniza", false)),
                    e("FAL", "Fallecimiento", Map.of("indemniza", false)),
                    e("FEP", "Fin de etapa productiva (aprendiz)", Map.of("indemniza", false)));
            case MOTIVO_AUSENTISMO -> List.of(
                    e("IGE", "Incapacidad por enfermedad general", aus("EPS", true, "IGE")),
                    e("IRL", "Incapacidad por accidente o enfermedad laboral", aus("ARL", true, "IRL")),
                    e("LMA", "Licencia de maternidad", aus("EPS", true, "LMA")),
                    e("LPA", "Licencia de paternidad", aus("EPS", true, "LMA")),
                    e("LUT", "Licencia de luto", aus("EMPRESA", true, "VAC-LR")),
                    e("CAL", "Calamidad doméstica", aus("EMPRESA", true, "VAC-LR")),
                    e("PRM", "Permiso remunerado", aus("EMPRESA", true, "VAC-LR")),
                    e("LNR", "Licencia no remunerada", aus("NINGUNO", false, "SLN")),
                    e("SUS", "Suspensión disciplinaria", aus("NINGUNO", false, "SLN")));
            case RIESGO_ARL -> List.of(
                    e("I", "Clase I — riesgo mínimo", Map.of("porcentaje", new BigDecimal("0.522"))),
                    e("II", "Clase II — riesgo bajo", Map.of("porcentaje", new BigDecimal("1.044"))),
                    e("III", "Clase III — riesgo medio", Map.of("porcentaje", new BigDecimal("2.436"))),
                    e("IV", "Clase IV — riesgo alto", Map.of("porcentaje", new BigDecimal("4.350"))),
                    e("V", "Clase V — riesgo máximo", Map.of("porcentaje", new BigDecimal("6.960"))));
            case CALENDARIO_LABORAL -> List.of(
                    e("LV", "Lunes a viernes", dias(5)),
                    e("LS", "Lunes a sábado", dias(6)),
                    e("LD", "Toda la semana (turnos)", dias(7)));
        };
    }

    private static Elemento e(String codigo, String nombre) {
        return new Elemento(codigo, nombre, Map.of());
    }

    private static Elemento e(String codigo, String nombre, Map<String, Object> atributos) {
        return new Elemento(codigo, nombre, atributos);
    }

    /** Calendario que trabaja los primeros {@code n} días de la semana (lunes = 1), sin festivos. */
    private static Map<String, Object> dias(int n) {
        String[] nombres = { "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo" };
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < nombres.length; i++) m.put(nombres[i], i < n);
        m.put("trabajaFestivos", n == 7);
        return m;
    }

    private static Map<String, Object> aus(String pagador, boolean remunerado, String codigoPila) {
        return Map.of("pagador", pagador, "remunerado", remunerado, "codigoPila", codigoPila);
    }
}
