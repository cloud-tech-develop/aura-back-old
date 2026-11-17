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
 *   <li>Pasar un tope exige autorización: si todas las novedades de ese concepto
 *       están APROBADAS, pasa. Las horas trabajadas se pagan aunque excedan el
 *       límite legal (el exceso es responsabilidad del empleador, no del pago);
 *       las que vienen de asistencia o frentes ya llegan aprobadas por el supervisor.</li>
 * </ul>
 */
public final class ReglasTopesHoras {

    /**
     * Una novedad de horas: tipo (= código de concepto), cantidad de horas, fecha
     * (puede faltar) y si ya está aprobada.
     */
    public record NovedadHoras(String tipo, BigDecimal horas, LocalDate fecha, boolean aprobada) {

        public NovedadHoras(String tipo, BigDecimal horas, LocalDate fecha) {
            this(tipo, horas, fecha, false);
        }
    }

    /** Sufijo de los mensajes: cómo se pasa el tope. */
    static final String COMO_AUTORIZAR = " Autorice la novedad para pagar las horas por encima del tope.";

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
        Map<String, Boolean> todasAprobadas = new LinkedHashMap<>();
        for (NovedadHoras n : novedades) {
            if (!porCodigo.containsKey(n.tipo())) continue;
            todasAprobadas.merge(n.tipo(), n.aprobada(), Boolean::logicalAnd);
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
            if (excede(e.getValue(), t.topeDia()) && !aprobadas(todasAprobadas, partes[0]))
                return Optional.of(t.tipoNombre() + ": " + texto(e.getValue()) + " horas el " + partes[1]
                        + " y el cargo permite " + texto(t.topeDia()) + " al día." + COMO_AUTORIZAR);
        }
        for (Map.Entry<String, BigDecimal> e : porSemana.entrySet()) {
            String codigo = e.getKey().split("\\|")[0];
            TopeHoras t = porCodigo.get(codigo);
            if (excede(e.getValue(), t.topeSemana()) && !aprobadas(todasAprobadas, codigo))
                return Optional.of(t.tipoNombre() + ": " + texto(e.getValue()) + " horas en una semana y el cargo permite "
                        + texto(t.topeSemana()) + "." + COMO_AUTORIZAR);
        }
        for (Map.Entry<String, BigDecimal> e : total.entrySet()) {
            TopeHoras t = porCodigo.get(e.getKey());
            if (excede(e.getValue(), t.topeMes()) && !aprobadas(todasAprobadas, e.getKey()))
                return Optional.of(t.tipoNombre() + ": " + texto(e.getValue()) + " horas en el período y el cargo permite "
                        + texto(t.topeMes()) + " al mes." + COMO_AUTORIZAR);
        }
        return Optional.empty();
    }

    private static boolean aprobadas(Map<String, Boolean> todasAprobadas, String codigo) {
        return Boolean.TRUE.equals(todasAprobadas.get(codigo));
    }

    private static boolean excede(BigDecimal valor, BigDecimal tope) {
        return tope != null && valor.compareTo(tope) > 0;
    }

    private static String texto(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
