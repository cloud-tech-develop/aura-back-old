package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;

/**
 * Lo que ya se le liquidó al contrato en el mismo mes, en nóminas confirmadas
 * anteriores a la que se está liquidando (la primera quincena cuando se liquida la
 * segunda, un pago adicional del mes…).
 *
 * <p>La seguridad social y la retención en la fuente son mensuales: el tope de 25
 * SMMLV, el límite del 40 % de pagos no salariales (Ley 1393), el umbral de la
 * exoneración (Ley 1607), el rango del fondo de solidaridad y la tabla de
 * retención se miden con todo el mes. Sin este acumulado, una nómina quincenal los
 * calcula con media remuneración y descuenta de menos.
 *
 * @param nominas     cuántas nóminas previas del mes hay (0 = es la primera)
 * @param ibc         IBC ya cotizado en el mes
 * @param salarial    devengado salarial (constituye IBC, sin auxilio de transporte)
 * @param noSalarial  devengado no salarial (sin auxilio de transporte)
 * @param devengado   total devengado (ingreso bruto para la retención)
 * @param aportes     aportes obligatorios del empleado (salud, pensión y FSP)
 * @param fsp         fondo de solidaridad ya descontado
 * @param retencion   retención en la fuente ya practicada
 */
public record AcumuladoMes(int nominas, BigDecimal ibc, BigDecimal salarial, BigDecimal noSalarial,
                           BigDecimal devengado, BigDecimal aportes, BigDecimal fsp, BigDecimal retencion) {

    /** Primera nómina del mes: nada acumulado. */
    public static final AcumuladoMes VACIO = new AcumuladoMes(0, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    /** Hay nóminas previas en el mes. */
    public boolean hayPrevias() {
        return nominas > 0;
    }
}
