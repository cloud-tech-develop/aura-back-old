package com.cloud_technological.aura_pos.services.nomina.electronica;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * ¿Las nóminas confirmadas cubren todo el mes del empleado? Solo entonces se envía la
 * nómina electrónica (un documento por empleado y mes, con todo lo pagado).
 *
 * <p>El mes del empleado va del día 1 (o su fecha de ingreso, si entró ese mes) al
 * último día (o su fecha de retiro, si salió ese mes). Así la misma regla sirve para
 * pago mensual (una nómina), quincenal (dos), semanal (cuatro o cinco) y para el
 * retiro a mitad de mes (con la liquidación definitiva).
 *
 * @param completo   las nóminas cubren todo el mes del empleado
 * @param motivo     por qué no está completo (null si lo está)
 */
public record CoberturaMes(boolean completo, String motivo) {

    /**
     * @param mes             mes del documento
     * @param coberturaInicio inicio del primer período periódico confirmado (null = ninguno)
     * @param coberturaFin    fin del último período periódico confirmado (null = ninguno)
     * @param contratoInicio  ingreso del empleado
     * @param contratoFin     fin del contrato (null = indefinido)
     * @param retirado        el contrato terminó (o hay liquidación definitiva en el mes)
     */
    public static CoberturaMes evaluar(YearMonth mes, LocalDate coberturaInicio, LocalDate coberturaFin,
                                       LocalDate contratoInicio, LocalDate contratoFin, boolean retirado) {
        LocalDate desde = mes.atDay(1);
        if (contratoInicio != null && contratoInicio.isAfter(desde)) desde = contratoInicio;
        LocalDate hasta = mes.atEndOfMonth();
        if (retirado && contratoFin != null && contratoFin.isBefore(hasta)) hasta = contratoFin;

        if (coberturaInicio == null || coberturaFin == null)
            return new CoberturaMes(false, "No hay nóminas del período confirmadas en el mes.");
        if (coberturaFin.isBefore(hasta))
            return new CoberturaMes(false, "Falta liquidar y confirmar la nómina del " + coberturaFin.plusDays(1)
                    + " al " + hasta + " (p. ej. la 2ª quincena).");
        if (coberturaInicio.isAfter(desde))
            return new CoberturaMes(false, "Falta la nómina del " + desde + " al " + coberturaInicio.minusDays(1) + ".");
        return new CoberturaMes(true, null);
    }
}
