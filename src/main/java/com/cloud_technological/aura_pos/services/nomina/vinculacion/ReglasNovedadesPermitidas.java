package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.Set;

import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionReglasQueryRepository.NovedadPermitida;

/**
 * Novedades permitidas de la vinculación contra las novedades del período (R3.2d).
 * Reglas puras, sin acceso a datos.
 *
 * <ul>
 *   <li>Sin lista de permitidas, la vinculación no restringe nada.</li>
 *   <li>Con lista, una novedad que paga (horas extra, bono, comisión, otro devengo)
 *       debe estar en ella. Las ausencias legales (incapacidad, licencias,
 *       vacaciones) y las deducciones nunca se restringen.</li>
 *   <li>La suma de cantidades y la de valores de cada tipo no pueden pasar su tope,
 *       salvo que la fila "requiera autorización" y todas las novedades de ese tipo
 *       estén aprobadas.</li>
 * </ul>
 */
public final class ReglasNovedadesPermitidas {

    /** Ausencias y licencias que la ley obliga a reconocer: no dependen de la vinculación. */
    private static final Set<String> SIEMPRE_PERMITIDAS = Set.of(
            "INCAPACIDAD", "LICENCIA_REMUNERADA", "LICENCIA_NO_REMUNERADA", "LICENCIA_MATERNIDAD", "VACACIONES", "RETROACTIVO");

    /**
     * Una novedad del período: tipo (= código de concepto), cantidad, valor, si es
     * deducción y si ya está aprobada (estado APROBADA).
     */
    public record Novedad(String tipo, BigDecimal cantidad, BigDecimal valor, boolean deduccion, boolean aprobada) {

        public Novedad(String tipo, BigDecimal cantidad, BigDecimal valor, boolean deduccion) {
            this(tipo, cantidad, valor, deduccion, false);
        }
    }

    private ReglasNovedadesPermitidas() {
    }

    /**
     * Valida las novedades del período.
     *
     * @return la primera regla incumplida, en palabras para el usuario; vacío si cumple
     */
    public static Optional<String> validar(List<NovedadPermitida> permitidas, List<Novedad> novedades) {
        return validar(permitidas, novedades, codigo -> codigo);
    }

    /**
     * Valida las novedades del período.
     *
     * @param etiqueta cómo nombrar un concepto en el mensaje (número y nombre, nunca la clave interna)
     * @return la primera regla incumplida, en palabras para el usuario; vacío si cumple
     */
    public static Optional<String> validar(List<NovedadPermitida> permitidas, List<Novedad> novedades,
                                           Function<String, String> etiqueta) {
        if (permitidas.isEmpty()) return Optional.empty();

        Map<String, NovedadPermitida> porCodigo = new LinkedHashMap<>();
        permitidas.forEach(p -> porCodigo.put(p.codigo(), p));

        Map<String, BigDecimal> cantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        Map<String, Boolean> todasAprobadas = new LinkedHashMap<>();
        for (Novedad n : novedades) {
            if (n.deduccion() || SIEMPRE_PERMITIDAS.contains(n.tipo())) continue;
            if (!porCodigo.containsKey(n.tipo()))
                return Optional.of("La novedad " + etiqueta.apply(n.tipo()) + " no está permitida en su vinculación ni en su cargo.");
            cantidades.merge(n.tipo(), nz(n.cantidad()), BigDecimal::add);
            valores.merge(n.tipo(), nz(n.valor()), BigDecimal::add);
            todasAprobadas.merge(n.tipo(), n.aprobada(), Boolean::logicalAnd);
        }

        for (Map.Entry<String, BigDecimal> e : cantidades.entrySet()) {
            NovedadPermitida p = porCodigo.get(e.getKey());
            // Pasar el tope con autorización: vale si todas las novedades del tipo están aprobadas.
            if (p.requiereAutorizacion() && Boolean.TRUE.equals(todasAprobadas.get(e.getKey()))) continue;
            String sufijo = p.requiereAutorizacion() ? " Requiere autorización: apruebe la novedad para pasar el tope." : "";
            if (p.topeCantidad() != null && e.getValue().compareTo(p.topeCantidad()) > 0)
                return Optional.of("La novedad " + etiqueta.apply(e.getKey()) + " suma " + e.getValue().stripTrailingZeros().toPlainString()
                        + " y su tope por período es " + p.topeCantidad().stripTrailingZeros().toPlainString() + "." + sufijo);
            BigDecimal valor = valores.get(e.getKey());
            if (p.topeValor() != null && valor.compareTo(p.topeValor()) > 0)
                return Optional.of("La novedad " + etiqueta.apply(e.getKey()) + " suma $" + valor.stripTrailingZeros().toPlainString()
                        + " y su tope por período es $" + p.topeValor().stripTrailingZeros().toPlainString() + "." + sufijo);
        }
        return Optional.empty();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
