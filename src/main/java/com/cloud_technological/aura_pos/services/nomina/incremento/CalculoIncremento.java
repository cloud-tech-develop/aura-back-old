package com.cloud_technological.aura_pos.services.nomina.incremento;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Nuevo salario de un incremento (rediseño R8, §8). Puro, sin acceso a datos.
 */
public final class CalculoIncremento {

    /** Cómo se calcula el nuevo salario. */
    public enum Tipo {
        /** actual × (1 + valor %). */
        PORCENTAJE,
        /** actual + valor. */
        VALOR_ADICIONAL,
        /** valor (el mismo para todos). */
        NUEVO_VALOR,
        /** Sube al SMMLV a quien gane menos; a los demás no los cambia. */
        SMMLV
    }

    /** Redondeo del nuevo salario. */
    public enum Redondeo {
        NINGUNO, PESO, CENTENA, CENTENA_ARRIBA, CENTENA_ABAJO, MIL
    }

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal MIL = new BigDecimal("1000");

    private CalculoIncremento() {
    }

    /**
     * Nuevo salario.
     *
     * @param valor porcentaje, valor adicional o nuevo valor según el tipo (ignorado en SMMLV)
     * @return el nuevo salario redondeado; nunca menor que cero
     */
    public static BigDecimal nuevoSalario(BigDecimal actual, Tipo tipo, BigDecimal valor, BigDecimal smmlv,
            Redondeo redondeo) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        BigDecimal nuevo = switch (tipo) {
            case PORCENTAJE -> actual.add(actual.multiply(v).divide(CIEN, 6, RoundingMode.HALF_UP));
            case VALOR_ADICIONAL -> actual.add(v);
            case NUEVO_VALOR -> v;
            case SMMLV -> smmlv != null && actual.compareTo(smmlv) < 0 ? smmlv : actual;
        };
        nuevo = redondear(nuevo, redondeo);
        return nuevo.signum() < 0 ? BigDecimal.ZERO.setScale(2) : nuevo;
    }

    /** Redondea a dos decimales y luego al peso, a la centena o al mil. */
    public static BigDecimal redondear(BigDecimal v, Redondeo r) {
        return switch (r) {
            case NINGUNO -> v.setScale(2, RoundingMode.HALF_UP);
            case PESO -> v.setScale(0, RoundingMode.HALF_UP).setScale(2);
            case CENTENA -> multiplo(v, CIEN, RoundingMode.HALF_UP);
            case CENTENA_ARRIBA -> multiplo(v, CIEN, RoundingMode.CEILING);
            case CENTENA_ABAJO -> multiplo(v, CIEN, RoundingMode.FLOOR);
            case MIL -> multiplo(v, MIL, RoundingMode.HALF_UP);
        };
    }

    private static BigDecimal multiplo(BigDecimal v, BigDecimal m, RoundingMode modo) {
        return v.divide(m, 0, modo).multiply(m).setScale(2);
    }
}
