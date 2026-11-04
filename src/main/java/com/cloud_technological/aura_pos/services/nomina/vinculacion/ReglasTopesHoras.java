package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionReglasQueryRepository.TopeHoras;

/**
 * Máximo de horas extra y recargos del cargo contra las novedades del período
 * (F6 de docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md). Reglas puras, sin acceso a datos.
 *
 * <ul>
 *   <li>Por día y por semana se cuentan solo las novedades con fecha.</li>
 *   <li>El tope mensual se compara con el total del período (una quincena nunca
 *       lo pasa si el mes no lo pasa).</li>
 *   <li>Los topes son de horas: la autorización no los levanta (el de horas extra
 *       es además un límite legal).</li>
 * </ul>
 */
public final class ReglasTopesHoras {

    /** Una novedad de horas: tipo (= código de concepto), cantidad de horas y fecha (puede faltar). */
    public record NovedadHoras(String tipo, BigDecimal horas, LocalDate fecha) {
    }

    private ReglasTopesHoras() {
    }

    /**
     * Valida las horas del período contra los topes del cargo.
     *
     * @return la primera regla incumplida, en palabras para el usuario; vacío si cumple
     */
    public static Optional<String> validar(List<TopeHoras> topes, List<NovedadHoras> novedades) {
        if (topes.isEmpty() || novedades.isEmpty()) return Optional.empty();
        Map<String, TopeHoras> porCodigo = new LinkedHashMap<>();
        topes.forEach(t -> porCodigo.put(t.codigo(), t));

        Map<String, BigDecimal> total = new LinkedHashMap<>();
        Map<String, BigDecimal> porDia = new LinkedHashMap<>();
        Map<String, BigDecimal> porSemana = new LinkedHashMap<>();
        for (NovedadHoras n : novedades) {
            if (!porCodigo.containsKey(n.tipo())) continue;
            BigDecimal horas = n.horas() != null ? n.horas() : BigDecimal.ZERO;
            total.merge(n.tipo(), horas, BigDecimal::add);
            if (n.fecha() != null) {
                porDia.merge(n.tipo() + "|" + n.fecha(), horas, BigDecimal::add);
                String semana = n.fecha().get(IsoFields.WEEK_BASED_YEAR) + "-"
                        + n.fecha().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                porSemana.merge(n.tipo() + "|" + semana, horas, BigDecimal::add);
            }
        }

        for (Map.Entry<String, BigDecimal> e : porDia.entrySet()) {
            String[] partes = e.getKey().split("\\|");
            TopeHoras t = porCodigo.get(partes[0]);
            if (excede(e.getValue(), t.topeDia()))
                return Optional.of(t.tipoNombre() + ": " + texto(e.getValue()) + " horas el " + partes[1]
                        + " y el cargo permite " + texto(t.topeDia()) + " al día.");
        }
        for (Map.Entry<String, BigDecimal> e : porSemana.entrySet()) {
            TopeHoras t = porCodigo.get(e.getKey().split("\\|")[0]);
            if (excede(e.getValue(), t.topeSemana()))
                return Optional.of(t.tipoNombre() + ": " + texto(e.getValue()) + " horas en una semana y el cargo permite "
                        + texto(t.topeSemana()) + ".");
        }
        for (Map.Entry<String, BigDecimal> e : total.entrySet()) {
            TopeHoras t = porCodigo.get(e.getKey());
            if (excede(e.getValue(), t.topeMes()))
                return Optional.of(t.tipoNombre() + ": " + texto(e.getValue()) + " horas en el período y el cargo permite "
                        + texto(t.topeMes()) + " al mes.");
        }
        return Optional.empty();
    }

    private static boolean excede(BigDecimal valor, BigDecimal tope) {
        return tope != null && valor.compareTo(tope) > 0;
    }

    private static String texto(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
