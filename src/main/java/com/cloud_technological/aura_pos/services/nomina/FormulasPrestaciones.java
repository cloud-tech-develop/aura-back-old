package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Fórmulas de prestaciones sociales y de la indemnización (CST), puras y sin acceso
 * a datos. Las usan el módulo de Prestaciones y la liquidación definitiva del
 * retiro (R5), para que las dos den lo mismo.
 */
public final class FormulasPrestaciones {

    private static final BigDecimal D360 = new BigDecimal("360");
    private static final BigDecimal D720 = new BigDecimal("720");
    private static final BigDecimal TREINTA = new BigDecimal("30");
    private static final BigDecimal DIEZ = BigDecimal.TEN;
    private static final BigDecimal PCT_INT_CESANTIAS = new BigDecimal("0.12");

    private FormulasPrestaciones() {
    }

    /**
     * Días entre dos fechas (ambas inclusive) en base 360: el 31 y el último día del
     * mes cuentan como 30.
     */
    public static int diasBase360(LocalDate inicio, LocalDate fin) {
        int d1 = inicio.getDayOfMonth();
        int d2 = fin.getDayOfMonth();
        if (d1 == 31) d1 = 30;
        boolean finDeMes = fin.getDayOfMonth() == fin.lengthOfMonth();
        if (d2 == 31 || finDeMes) d2 = 30;
        int meses = (fin.getYear() - inicio.getYear()) * 12 + (fin.getMonthValue() - inicio.getMonthValue());
        return Math.max(0, meses * 30 + (d2 - d1) + 1);
    }

    /**
     * Valor de una prestación.
     *
     * @param tipo               PRIMA | CESANTIAS | INTERESES_CESANTIAS | VACACIONES
     * @param promedioVariable   promedio mensual de lo salarial variable (puede ser null)
     * @param vacacionesDisfrute VACACIONES: true = pago del descanso (base/30 × días
     *                           hábiles); false = causación en la definitiva (base × días / 720)
     */
    public static BigDecimal valor(String tipo, BigDecimal salario, BigDecimal auxilio,
            BigDecimal promedioVariable, int dias, boolean vacacionesDisfrute) {
        BigDecimal d = new BigDecimal(dias);
        BigDecimal prom = promedioVariable != null ? promedioVariable : BigDecimal.ZERO;
        BigDecimal baseAux = salario.add(auxilio).add(prom);
        BigDecimal baseVac = salario.add(prom);
        switch (tipo) {
            case "PRIMA", "CESANTIAS":
                return baseAux.multiply(d).divide(D360, 2, RoundingMode.HALF_UP);
            case "VACACIONES":
                return vacacionesDisfrute
                        ? baseVac.multiply(d).divide(TREINTA, 2, RoundingMode.HALF_UP)
                        : baseVac.multiply(d).divide(D720, 2, RoundingMode.HALF_UP);
            case "INTERESES_CESANTIAS": {
                // Intereses = cesantías del período × 12% × días/360.
                BigDecimal cesantias = baseAux.multiply(d).divide(D360, 6, RoundingMode.HALF_UP);
                return cesantias.multiply(PCT_INT_CESANTIAS).multiply(d).divide(D360, 2, RoundingMode.HALF_UP);
            }
            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * Pago de vacaciones por días hábiles (disfrutadas, compensadas o pendientes al
     * retiro): (salario + promedio variable) ÷ 30 × días. Admite días con decimales.
     */
    public static BigDecimal valorVacaciones(BigDecimal salario, BigDecimal promedioVariable, BigDecimal dias) {
        BigDecimal prom = promedioVariable != null ? promedioVariable : BigDecimal.ZERO;
        return salario.add(prom).multiply(dias).divide(TREINTA, 2, RoundingMode.HALF_UP);
    }

    /**
     * Días de salario de la indemnización por despido sin justa causa (CST art. 64).
     *
     * <ul>
     *   <li>INDEFINIDO, salario menor a 10 SMMLV: 30 días el primer año (proporcional)
     *       + 20 por cada año adicional; con 10 SMMLV o más: 20 + 15.</li>
     *   <li>FIJO: los días que faltaban para el fin pactado.</li>
     *   <li>OBRA_LABOR: el mínimo legal de 15 días.</li>
     *   <li>Otros contratos: 0 (no aplica).</li>
     * </ul>
     *
     * @param finPactado fin pactado del contrato FIJO (antes de sobrescribirlo con el retiro)
     */
    public static BigDecimal diasIndemnizacion(String tipoContrato, BigDecimal salario, BigDecimal smmlv,
            LocalDate ingreso, LocalDate fechaRetiro, LocalDate finPactado) {
        switch (tipoContrato == null ? "" : tipoContrato) {
            case "INDEFINIDO": {
                int antiguedad = ingreso != null ? diasBase360(ingreso, fechaRetiro) : 0;
                boolean menorA10 = smmlv != null && smmlv.signum() > 0 && salario.compareTo(smmlv.multiply(DIEZ)) < 0;
                BigDecimal primerAnio = menorA10 ? new BigDecimal("30") : new BigDecimal("20");
                BigDecimal adicional = menorA10 ? new BigDecimal("20") : new BigDecimal("15");
                BigDecimal anios = new BigDecimal(antiguedad).divide(D360, 6, RoundingMode.HALF_UP);
                if (anios.compareTo(BigDecimal.ONE) <= 0) return primerAnio.multiply(anios);
                return primerAnio.add(adicional.multiply(anios.subtract(BigDecimal.ONE)));
            }
            case "FIJO":
                return finPactado != null && finPactado.isAfter(fechaRetiro)
                        ? new BigDecimal(diasBase360(fechaRetiro, finPactado))
                        : BigDecimal.ZERO;
            case "OBRA_LABOR":
                return new BigDecimal("15");
            default:
                return BigDecimal.ZERO;
        }
    }

    /** Valor de la indemnización: salario diario (salario / 30) × días. */
    public static BigDecimal valorIndemnizacion(BigDecimal salario, BigDecimal dias) {
        return salario.divide(TREINTA, 6, RoundingMode.HALF_UP).multiply(dias).setScale(2, RoundingMode.HALF_UP);
    }
}
