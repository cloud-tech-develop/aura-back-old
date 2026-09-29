package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Desglose paso a paso de un cálculo.
 *
 * <p>Es la diferencia entre "el sistema dice 1.234.567" y poder explicarle al
 * empleado de dónde salió ese número. Se persiste como JSONB en
 * {@code nomina_detalle.traza}.
 *
 * <p><b>Snapshot: no se recalcula.</b> Si mañana cambia la tarifa de salud, la
 * nómina de marzo debe seguir explicándose con la tarifa de marzo.
 */
public class Traza {

    /** Un paso del cálculo. */
    public record Paso(String nombre, String valor) {}

    private final List<Paso> pasos = new ArrayList<>();

    public static Traza vacia() {
        return new Traza();
    }

    /** Traza de un solo paso: un valor sin cálculo detrás. */
    public static Traza de(String nombre, BigDecimal valor) {
        return new Traza().paso(nombre, valor);
    }

    /** Traza de "base × porcentaje = resultado", el caso mayoritario. */
    public static Traza calculo(String nombreBase, BigDecimal base,
                                BigDecimal porcentaje, BigDecimal resultado) {
        return new Traza()
                .paso(nombreBase, base)
                .paso("Tarifa", porcentaje + " %")
                .paso("Resultado", resultado);
    }

    public Traza paso(String nombre, BigDecimal valor) {
        pasos.add(new Paso(nombre, valor != null ? valor.toPlainString() : "0"));
        return this;
    }

    public Traza paso(String nombre, String valor) {
        pasos.add(new Paso(nombre, valor));
        return this;
    }

    public List<Paso> getPasos() {
        return List.copyOf(pasos);
    }

    /**
     * Serializa a JSON.
     *
     * <p>A mano y no con Jackson: es una estructura de dos campos, no vale la
     * pena inyectar un ObjectMapper en una clase que quiere ser testeable sin
     * contexto de Spring.
     */
    public String aJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < pasos.size(); i++) {
            Paso p = pasos.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"paso\":\"").append(escapar(p.nombre()))
              .append("\",\"valor\":\"").append(escapar(p.valor())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String escapar(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return aJson();
    }
}
