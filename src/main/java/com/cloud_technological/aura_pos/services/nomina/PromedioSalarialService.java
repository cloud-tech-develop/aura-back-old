package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.repositories.nomina.NominaNovedadJPARepository;

/**
 * Promedio del salario variable para la base prestacional (B-01).
 *
 * <h2>Por qué existe</h2>
 * La base de prima, cesantías, intereses y vacaciones <b>no es solo el salario
 * básico</b>: incluye el promedio de lo devengado variable salarial (comisiones,
 * horas extra, recargos, dominicales). Antes de B-01 la liquidación tomaba solo
 * {@code salario + auxilio}, subpagando de forma sistemática a cualquier empleado
 * con pago variable — un riesgo directo de demanda laboral y hallazgo UGPP.
 *
 * <h2>Matiz de vacaciones</h2>
 * En vacaciones el trabajo suplementario (horas extra) <b>no</b> entra a la base;
 * los recargos y comisiones sí. Por eso el promedio se pide con
 * {@code excluirHorasExtra = true} solo para vacaciones.
 */
@Component
public class PromedioSalarialService {

    private static final BigDecimal TREINTA = new BigDecimal("30");

    private final NominaNovedadJPARepository novedadRepo;

    public PromedioSalarialService(NominaNovedadJPARepository novedadRepo) {
        this.novedadRepo = novedadRepo;
    }

    /**
     * Promedio <b>mensual</b> de lo devengado variable salarial en una ventana.
     *
     * <p>Se toma el total variable de las nóminas del empleado que cruzan
     * {@code [desde, hasta]} y se divide entre los meses de la ventana
     * ({@code diasReferencia / 30}). El resultado es un valor mensual, listo para
     * sumarse al salario antes de prorratear por días.
     *
     * @param diasReferencia    días de la ventana en convención comercial (base-360).
     * @param excluirHorasExtra {@code true} para vacaciones (no suma horas extra).
     * @return promedio mensual, o {@link BigDecimal#ZERO} si no hay variable.
     */
    public BigDecimal promedioMensualVariable(Integer empresaId, Long empleadoId,
                                              LocalDate desde, LocalDate hasta,
                                              int diasReferencia, boolean excluirHorasExtra) {
        if (empleadoId == null || diasReferencia <= 0) return BigDecimal.ZERO;

        BigDecimal total = novedadRepo.sumVariableSalarial(
                empresaId, empleadoId, desde, hasta, excluirHorasExtra);
        if (total == null || total.signum() == 0) return BigDecimal.ZERO;

        BigDecimal meses = BigDecimal.valueOf(diasReferencia).divide(TREINTA, 6, RoundingMode.HALF_UP);
        if (meses.signum() == 0) return BigDecimal.ZERO;

        return total.divide(meses, 2, RoundingMode.HALF_UP);
    }
}
