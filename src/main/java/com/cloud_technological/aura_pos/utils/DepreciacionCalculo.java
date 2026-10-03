package com.cloud_technological.aura_pos.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cuota de depreciación de un mes (Fase 3). Función pura: no lee la base.
 *
 * <p>Todas las cuotas son prospectivas: lo que falta por depreciar se reparte
 * en los meses que le quedan de vida. Con una vida y un costo que no cambian
 * da la cuota fija de siempre ((costo − residual) / vida); cuando una adición
 * sube el costo o alarga la vida, las cuotas siguientes se ajustan solas, y la
 * última absorbe el redondeo. Antes se dividía lo que quedaba entre la vida
 * TOTAL, y la cuota bajaba cada mes: el activo nunca terminaba de depreciarse.
 */
public final class DepreciacionCalculo {

    public static final String LINEA_RECTA = "LINEA_RECTA";
    public static final String SALDO_DECRECIENTE = "SALDO_DECRECIENTE";
    public static final String UNIDADES_PRODUCCION = "UNIDADES_PRODUCCION";

    private DepreciacionCalculo() {
    }

    /**
     * @param costo            valor de compra + adiciones
     * @param residual         lo que no se deprecia
     * @param acumulada        depreciación ya registrada
     * @param vidaMeses        vida útil total (incluye los meses que sumaron las adiciones)
     * @param mesesDepreciados cuotas ya registradas
     * @param unidadesMes      solo UNIDADES_PRODUCCION: uso del mes
     * @param unidadesTotales  solo UNIDADES_PRODUCCION: vida en unidades
     * @return cuota del mes, nunca mayor que lo que falta ni negativa
     */
    public static BigDecimal cuota(String metodo, BigDecimal costo, BigDecimal residual, BigDecimal acumulada,
            int vidaMeses, int mesesDepreciados, BigDecimal unidadesMes, BigDecimal unidadesTotales) {
        BigDecimal pendiente = nz(costo).subtract(nz(residual)).subtract(nz(acumulada));
        if (pendiente.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int restantes = Math.max(1, vidaMeses - mesesDepreciados);

        BigDecimal cuota = switch (metodo != null ? metodo : LINEA_RECTA) {
            case UNIDADES_PRODUCCION -> {
                if (unidadesTotales == null || unidadesTotales.signum() <= 0
                        || unidadesMes == null || unidadesMes.signum() <= 0) {
                    yield BigDecimal.ZERO;
                }
                yield nz(costo).subtract(nz(residual)).multiply(unidadesMes)
                        .divide(unidadesTotales, 2, RoundingMode.HALF_UP);
            }
            case SALDO_DECRECIENTE -> {
                // Doble saldo decreciente sobre el valor en libros; cuando la
                // línea recta de lo que queda da más, se pasa a ella para
                // terminar en la vida útil (práctica usual del método).
                BigDecimal enLibros = nz(costo).subtract(nz(acumulada));
                BigDecimal doble = enLibros.multiply(BigDecimal.valueOf(2))
                        .divide(BigDecimal.valueOf(Math.max(1, vidaMeses)), 2, RoundingMode.HALF_UP);
                BigDecimal recta = pendiente.divide(BigDecimal.valueOf(restantes), 2, RoundingMode.HALF_UP);
                yield doble.max(recta);
            }
            default -> pendiente.divide(BigDecimal.valueOf(restantes), 2, RoundingMode.HALF_UP);
        };
        return cuota.min(pendiente).max(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
