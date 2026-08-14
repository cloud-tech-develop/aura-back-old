package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Cálculo de incapacidades y licencia de maternidad (F1).
 *
 * <h2>Reglas por tipo</h2>
 * <ul>
 *   <li><b>Enfermedad general (EG):</b> 66.67 % (2/3) del IBC. Los <b>2 primeros
 *       días</b> los paga el <b>empleador</b>; del 3.º en adelante, la <b>EPS</b>.
 *       Piso: el día no puede quedar por debajo del salario mínimo diario.</li>
 *   <li><b>Riesgo laboral (AT/EL):</b> 100 % del IBC desde el día 1, a cargo de la
 *       <b>ARL</b>.</li>
 *   <li><b>Maternidad/paternidad:</b> 100 % del IBC, a cargo de la <b>EPS</b>.</li>
 * </ul>
 *
 * <p><b>⚠️ Pendiente:</b> EG mayor a 90 días baja al 50 % y el promedio con salario
 * variable. Para el caso típico (incapacidades cortas, salario fijo) esto basta;
 * verificar con contador antes de casos largos.
 */
@Component
public class CalculadoraIncapacidad {

    private static final BigDecimal TREINTA = new BigDecimal("30");
    private static final BigDecimal DOS = new BigDecimal("2");
    private static final BigDecimal TRES = new BigDecimal("3");
    private static final int ESCALA = 2;

    /** Resultado: cuánto recibe el trabajador y cómo se reparte quién lo paga. */
    public record Resultado(BigDecimal total, BigDecimal valorEmpleador,
                            BigDecimal valorEntidad, BigDecimal valorDia,
                            String quienPaga, String descripcion) {}

    /**
     * @param subtipo  GENERAL (EG), RIESGO_LABORAL (AT/EL) o MATERNIDAD.
     * @param dias     días de la incapacidad.
     * @param salario  salario mensual base (IBC).
     * @param smmlv    salario mínimo mensual vigente.
     */
    public Resultado calcular(String subtipo, int dias, BigDecimal salario, BigDecimal smmlv) {
        if (dias <= 0 || salario == null) {
            return new Resultado(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, "—", "Sin días");
        }
        BigDecimal ibcDiario = salario.divide(TREINTA, ESCALA + 2, RoundingMode.HALF_UP);
        String st = subtipo != null ? subtipo.toUpperCase() : "GENERAL";

        return switch (st) {
            case "RIESGO_LABORAL", "LABORAL", "ARL" -> cienPorCiento(dias, ibcDiario, "ARL",
                    "Incapacidad por riesgo laboral " + dias + " días (100% ARL)");
            case "MATERNIDAD", "PATERNIDAD" -> cienPorCiento(dias, ibcDiario, "EPS",
                    "Licencia de maternidad/paternidad " + dias + " días (100% EPS)");
            default -> enfermedadGeneral(dias, ibcDiario, smmlv);
        };
    }

    private Resultado cienPorCiento(int dias, BigDecimal ibcDiario, String quien, String desc) {
        BigDecimal valorDia = ibcDiario.setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal total = valorDia.multiply(BigDecimal.valueOf(dias)).setScale(ESCALA, RoundingMode.HALF_UP);
        // A cargo de la entidad (ARL/EPS); el empleador no asume valor.
        return new Resultado(total, BigDecimal.ZERO, total, valorDia, quien, desc);
    }

    private Resultado enfermedadGeneral(int dias, BigDecimal ibcDiario, BigDecimal smmlv) {
        // 2/3 del IBC, con piso en el salario mínimo diario.
        BigDecimal dosTercios = ibcDiario.multiply(DOS).divide(TRES, ESCALA + 2, RoundingMode.HALF_UP);
        BigDecimal smmlvDiario = (smmlv != null ? smmlv : BigDecimal.ZERO)
                .divide(TREINTA, ESCALA + 2, RoundingMode.HALF_UP);
        BigDecimal valorDia = dosTercios.max(smmlvDiario).setScale(ESCALA, RoundingMode.HALF_UP);

        int diasEmpleador = Math.min(dias, 2);
        int diasEntidad = Math.max(0, dias - 2);

        BigDecimal valorEmpleador = valorDia.multiply(BigDecimal.valueOf(diasEmpleador))
                .setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal valorEntidad = valorDia.multiply(BigDecimal.valueOf(diasEntidad))
                .setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal total = valorEmpleador.add(valorEntidad);

        String desc = "Incapacidad enfermedad general " + dias + " días (66.67%): "
                + diasEmpleador + " empleador + " + diasEntidad + " EPS";
        String quien = diasEntidad > 0 ? "Empleador (2 días) + EPS" : "Empleador";
        return new Resultado(total, valorEmpleador, valorEntidad, valorDia, quien, desc);
    }
}
