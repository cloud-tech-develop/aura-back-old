package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de seguridad social y retención que se miden por MES, aplicadas a una
 * nómina que puede ser solo una parte del mes (quincena). Cada regla calcula el
 * valor del mes completo y le resta lo que ya se reconoció en las nóminas previas
 * del mismo mes ({@link AcumuladoMes}); así la suma de las quincenas da lo mismo
 * que una nómina mensual.
 */
public final class ReglasMensuales {

    /** Ley 1393 de 2010, art. 30: los pagos no salariales no pueden pasar del 40 %. */
    public static final BigDecimal LIMITE_NO_SALARIAL = new BigDecimal("0.40");

    private static final int ESCALA = 2;

    private ReglasMensuales() {
    }

    /**
     * Exceso de pagos no salariales sobre el 40 % de la remuneración total del mes
     * (Ley 1393): ese exceso entra al IBC.
     */
    public static BigDecimal excesoNoSalarial(BigDecimal salarial, BigDecimal noSalarial) {
        BigDecimal total = nz(salarial).add(nz(noSalarial));
        BigDecimal limite = total.multiply(LIMITE_NO_SALARIAL).setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal exceso = nz(noSalarial).subtract(limite);
        return exceso.signum() > 0 ? exceso : BigDecimal.ZERO;
    }

    /**
     * Parte del exceso Ley 1393 que le toca a esta nómina: el exceso del mes con
     * ella menos el que ya tenían las nóminas previas.
     */
    public static BigDecimal excesoNoSalarialDeEstaNomina(AcumuladoMes previo, BigDecimal salarial,
                                                          BigDecimal noSalarial) {
        BigDecimal mes = excesoNoSalarial(previo.salarial().add(nz(salarial)), previo.noSalarial().add(nz(noSalarial)));
        BigDecimal antes = excesoNoSalarial(previo.salarial(), previo.noSalarial());
        BigDecimal propio = mes.subtract(antes);
        return propio.signum() > 0 ? propio : BigDecimal.ZERO;
    }

    /**
     * Tope de IBC que le queda a esta nómina: el tope mensual (25 SMMLV) menos el
     * IBC ya cotizado en el mes. Nunca negativo.
     */
    public static BigDecimal topeIbcRestante(BigDecimal topeMensual, AcumuladoMes previo) {
        BigDecimal resta = nz(topeMensual).subtract(previo.ibc());
        return resta.signum() > 0 ? resta : BigDecimal.ZERO;
    }

    /** IBC del mes: el previo más el de esta nómina. */
    public static BigDecimal ibcMes(AcumuladoMes previo, BigDecimal ibcEstaNomina) {
        return previo.ibc().add(nz(ibcEstaNomina));
    }

    /**
     * Lo que falta descontar de un valor mensual (FSP, retención) dado lo ya
     * descontado en el mes. Si en la primera parte se descontó de más, no se
     * devuelve aquí: queda en cero.
     */
    public static BigDecimal pendiente(BigDecimal valorMes, BigDecimal yaDescontado) {
        BigDecimal p = nz(valorMes).subtract(nz(yaDescontado));
        return p.signum() > 0 ? p : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
