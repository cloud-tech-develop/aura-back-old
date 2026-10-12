package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Formato 2276 (ingresos y retenciones por rentas de trabajo): lleva cada concepto
 * pagado en la nómina del año a su columna, por el {@code codigo_dian} del concepto
 * (el mismo que usa la nómina electrónica). Lógica pura, sin Spring.
 */
public final class Clasificador2276 {

    /** Lo pagado de un concepto a un empleado en el año. */
    public record Pago(String codigo, String codigoDian, String clase, boolean constituyeIbc,
                       String claseDocumento, BigDecimal valor) {
    }

    /** Columnas de pagos que suman el total bruto. */
    private static final List<String> PAGOS = List.of(
            "SALARIOS", "EMOLUMENTOS", "BONOS_ELECTRONICOS", "EXCESO_ALIMENTACION", "HONORARIOS", "SERVICIOS",
            "COMISIONES", "PRESTACIONES", "VIATICOS", "GASTOS_REPRESENTACION", "COOPERATIVO", "OTROS",
            "CESANTIAS_PAGADAS", "CESANTIAS_FONDO", "CESANTIAS_TRADICIONAL", "PENSIONES");

    private static final Set<String> SALARIALES = Set.of(
            "Basico", "HED", "HEN", "HRN", "HEDDF", "HRDDF", "HENDF", "HRNDF",
            "Incapacidades", "LicenciaMP", "LicenciaR");

    private Clasificador2276() {
    }

    /** Columna del 2276 a la que va un pago (null = no se reporta). */
    public static String columna(Pago p) {
        String dian = p.codigoDian() != null ? p.codigoDian() : "";
        if ("DEDUCCION".equals(p.clase())) {
            return switch (dian) {
                case "Salud" -> "APORTE_SALUD";
                case "FondoPension", "FondoSP" -> "APORTE_PENSION";
                case "RetencionFuente" -> "RETENCION";
                default -> null;   // préstamos, embargos, libranzas: no son rentas de trabajo
            };
        }
        if (!"DEVENGADO".equals(p.clase())) {
            return null;   // aportes del empleador y provisiones no son pagos al trabajador
        }
        if (SALARIALES.contains(dian)) return "SALARIOS";
        return switch (dian) {
            case "Comisiones" -> "COMISIONES";
            case "Primas", "Vacaciones" -> "PRESTACIONES";
            case "Cesantias" -> "INT_CESANTIAS".equals(p.codigo())
                    || "LIQUIDACION_DEFINITIVA".equals(p.claseDocumento())
                    ? "CESANTIAS_PAGADAS" : "CESANTIAS_FONDO";
            case "Bonificaciones" -> p.constituyeIbc() ? "SALARIOS" : "OTROS";
            case "Transporte", "Indemnizacion" -> "OTROS";
            default -> p.constituyeIbc() ? "SALARIOS" : "OTROS";
        };
    }

    /**
     * Valores de un empleado por columna, con el total bruto (suma de las columnas de
     * pagos).
     */
    public static Map<String, BigDecimal> valores(List<Pago> pagos) {
        Map<String, BigDecimal> v = new LinkedHashMap<>();
        for (Pago p : pagos) {
            String col = columna(p);
            if (col != null && p.valor() != null) v.merge(col, p.valor(), BigDecimal::add);
        }
        BigDecimal bruto = BigDecimal.ZERO;
        for (String c : PAGOS) bruto = bruto.add(v.getOrDefault(c, BigDecimal.ZERO));
        v.put("TOTAL_BRUTO", bruto);
        v.putIfAbsent("SALARIOS", BigDecimal.ZERO);
        return v;
    }
}
