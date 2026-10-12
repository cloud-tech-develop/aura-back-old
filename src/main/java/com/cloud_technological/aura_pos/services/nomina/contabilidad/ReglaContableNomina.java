package com.cloud_technological.aura_pos.services.nomina.contabilidad;

import com.cloud_technological.aura_pos.entity.ConceptoContable;

/**
 * Cómo se contabiliza cada concepto de nómina (rediseño R4c, brechas N1/N3): la
 * cuenta del débito, la del crédito y a nombre de quién va el crédito. Reglas
 * puras, sin acceso a datos; las cuentas reales salen de la parametrización
 * contable de la empresa ({@link ConceptoContable}).
 *
 * <ul>
 *   <li><b>Devengado</b>: débito al gasto de personal (las prestaciones que se pagan,
 *       al pasivo provisionado); su crédito es el neto por pagar al empleado (no lleva
 *       crédito propio).</li>
 *   <li><b>Deducción</b>: crédito al pasivo de quien recibe el descuento (EPS, AFP,
 *       DIAN, terceros de préstamos y embargos); su débito es parte del neto.</li>
 *   <li><b>Aporte del empleador y provisión</b>: débito al gasto y crédito al pasivo.</li>
 * </ul>
 */
public final class ReglaContableNomina {

    /** A nombre de quién va el crédito. */
    public enum TerceroCredito {
        EMPLEADO, EPS, AFP, ARL, CCF, DIAN,
        /** Sin tercero (ICBF y SENA: no se afilian por empleado). */
        NINGUNO,
        /** Siempre el mismo tercero, elegido por el contador (p. ej. el juzgado de un embargo). */
        FIJO
    }

    /**
     * Regla de un concepto.
     *
     * @param debito  cuenta del gasto (null = el concepto no se debita, p. ej. una deducción)
     * @param credito cuenta del pasivo (null = va al neto por pagar, p. ej. un devengado)
     */
    public record Regla(ConceptoContable debito, ConceptoContable credito, TerceroCredito tercero) {
    }

    /** Crédito del neto a pagar al empleado. */
    public static final Regla NETO = new Regla(null, ConceptoContable.SALARIOS_POR_PAGAR, TerceroCredito.EMPLEADO);

    private ReglaContableNomina() {
    }

    /**
     * Regla del concepto.
     *
     * @param codigo código del concepto de nómina
     * @param clase  DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION
     */
    public static Regla para(String codigo, String clase) {
        return switch (clase) {
            case "DEVENGADO" -> devengado(codigo);
            case "DEDUCCION" -> deduccion(codigo);
            case "APORTE_EMPLEADOR" -> aporte(codigo);
            case "PROVISION" -> provision(codigo);
            default -> throw new IllegalArgumentException("Clase de concepto desconocida: " + clase);
        };
    }

    /**
     * Las prestaciones que se pagan (liquidación definitiva, vacaciones disfrutadas)
     * se debitan al pasivo que se provisionó mes a mes, no al gasto: el gasto ya se
     * reconoció con la provisión.
     */
    private static Regla devengado(String codigo) {
        ConceptoContable debito = switch (codigo) {
            case "CESANTIAS" -> ConceptoContable.CESANTIAS_POR_PAGAR;
            case "INT_CESANTIAS" -> ConceptoContable.INT_CESANTIAS_POR_PAGAR;
            case "PRIMA" -> ConceptoContable.PRIMA_POR_PAGAR;
            case "VACACIONES" -> ConceptoContable.VACACIONES_POR_PAGAR;
            case "INDEMNIZACION" -> ConceptoContable.NOMINA_INDEMNIZACION;
            default -> ConceptoContable.NOMINA_SUELDOS;
        };
        return new Regla(debito, null, TerceroCredito.EMPLEADO);
    }

    private static Regla deduccion(String codigo) {
        return switch (codigo) {
            case "DED_SALUD" -> new Regla(null, ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, TerceroCredito.EPS);
            case "DED_PENSION", "DED_FONDO_SOLIDARIDAD" ->
                    new Regla(null, ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, TerceroCredito.AFP);
            case "RETEFUENTE_P1", "RETEFUENTE_P2" ->
                    new Regla(null, ConceptoContable.RETEFUENTE_PRACTICADA, TerceroCredito.DIAN);
            // Préstamos, embargos y otros descuentos: el beneficiario aún no se registra
            // por novedad; queda a nombre del empleado para conciliar.
            default -> new Regla(null, ConceptoContable.OTRAS_DEDUCCIONES_POR_PAGAR, TerceroCredito.EMPLEADO);
        };
    }

    private static Regla aporte(String codigo) {
        return switch (codigo) {
            case "APO_SALUD" -> new Regla(ConceptoContable.NOMINA_APORTE_SALUD,
                    ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, TerceroCredito.EPS);
            case "APO_PENSION" -> new Regla(ConceptoContable.NOMINA_APORTE_PENSION,
                    ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, TerceroCredito.AFP);
            case "APO_ARL" -> new Regla(ConceptoContable.NOMINA_ARL,
                    ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, TerceroCredito.ARL);
            case "APO_CCF" -> new Regla(ConceptoContable.NOMINA_CAJA,
                    ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, TerceroCredito.CCF);
            default -> new Regla(ConceptoContable.NOMINA_SENA_ICBF,
                    ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, TerceroCredito.NINGUNO);
        };
    }

    private static Regla provision(String codigo) {
        return switch (codigo) {
            case "PRO_PRIMA" -> new Regla(ConceptoContable.NOMINA_PRIMA,
                    ConceptoContable.PRIMA_POR_PAGAR, TerceroCredito.EMPLEADO);
            case "PRO_CESANTIAS" -> new Regla(ConceptoContable.NOMINA_CESANTIAS,
                    ConceptoContable.CESANTIAS_POR_PAGAR, TerceroCredito.EMPLEADO);
            case "PRO_INT_CES" -> new Regla(ConceptoContable.NOMINA_INT_CESANTIAS,
                    ConceptoContable.INT_CESANTIAS_POR_PAGAR, TerceroCredito.EMPLEADO);
            default -> new Regla(ConceptoContable.NOMINA_VACACIONES,
                    ConceptoContable.VACACIONES_POR_PAGAR, TerceroCredito.EMPLEADO);
        };
    }
}
