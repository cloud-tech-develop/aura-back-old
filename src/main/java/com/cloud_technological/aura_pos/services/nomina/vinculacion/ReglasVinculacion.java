package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Reglas legales de la vinculación que no dependen de la base de datos (CST).
 * Devuelven el mensaje del incumplimiento o vacío si la regla se cumple.
 */
public final class ReglasVinculacion {

    /** CST art. 78: el período de prueba no puede pasar de dos meses. */
    static final int MESES_MAX_PRUEBA = 2;

    private ReglasVinculacion() {
    }

    /**
     * Período de prueba (CST art. 78): empieza con el contrato, dura máximo dos meses
     * y, en contratos a término fijo menores de un año, máximo la quinta parte del término.
     *
     * @param inicio     inicio de la vinculación
     * @param fin        fin pactado (null = indefinido)
     * @param finPrueba  último día del período de prueba (null = sin prueba)
     * @return el incumplimiento, o vacío
     */
    public static Optional<String> periodoPrueba(LocalDate inicio, LocalDate fin, LocalDate finPrueba) {
        if (finPrueba == null || inicio == null) return Optional.empty();
        if (finPrueba.isBefore(inicio))
            return Optional.of("El período de prueba no puede terminar antes del inicio de la vinculación.");
        if (finPrueba.isAfter(inicio.plusMonths(MESES_MAX_PRUEBA).minusDays(1)))
            return Optional.of("El período de prueba no puede pasar de dos meses (art. 78 CST).");
        if (fin != null) {
            if (finPrueba.isAfter(fin))
                return Optional.of("El período de prueba no puede terminar después del contrato.");
            long termino = ChronoUnit.DAYS.between(inicio, fin) + 1;
            long prueba = ChronoUnit.DAYS.between(inicio, finPrueba) + 1;
            if (fin.isBefore(inicio.plusYears(1)) && prueba * 5 > termino)
                return Optional.of("En contratos de menos de un año, la prueba no puede pasar de la quinta parte del término ("
                        + (termino / 5) + " días).");
        }
        return Optional.empty();
    }

    /**
     * Horas pactadas frente a la jornada máxima legal vigente (Ley 2101 de 2021).
     *
     * @param horasSemana horas pactadas (null = jornada completa)
     * @param jornadaLegal máximo legal en la fecha
     * @return el incumplimiento, o vacío
     */
    public static Optional<String> jornada(BigDecimal horasSemana, BigDecimal jornadaLegal) {
        if (horasSemana == null || jornadaLegal == null) return Optional.empty();
        if (horasSemana.compareTo(jornadaLegal) > 0)
            return Optional.of("La jornada pactada (" + horasSemana.stripTrailingZeros().toPlainString()
                    + " h) supera la máxima legal vigente de " + jornadaLegal.stripTrailingZeros().toPlainString()
                    + " h por semana.");
        return Optional.empty();
    }

    /**
     * Fase del aprendiz: obligatoria en contratos de aprendizaje y vacía en los demás.
     *
     * @return el incumplimiento, o vacío
     */
    public static Optional<String> faseAprendiz(String tipoContrato, String fase) {
        if ("APRENDIZAJE".equals(tipoContrato) && fase == null)
            return Optional.of("Indique si el aprendiz está en etapa lectiva o práctica.");
        return Optional.empty();
    }
}
