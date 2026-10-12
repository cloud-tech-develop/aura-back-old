package com.cloud_technological.aura_pos.services.conciliacion_fiscal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Renglones del reporte de conciliación fiscal (formatos 2516 y 2517): ESF
 * (patrimonio) y ERI (renta líquida), con el prefijo del PUC (Decreto 2650) que
 * cae en cada uno. Se clasifica por el prefijo más largo: 1355 gana sobre 13.
 *
 * <p>Es la estructura de las hojas ESF y ERI del formato, agrupada a nivel de
 * rubro, para que el contador vea lo contable contra lo fiscal y lleve los valores
 * al prevalidador de la DIAN. Las cuentas de orden (8, 9) y la 59 (cierre) no
 * entran: quedan como {@link #NO_APLICA}.
 */
public final class RenglonesFiscales {

    public enum Seccion {
        ESF_ACTIVO("Activos"),
        ESF_PASIVO("Pasivos"),
        ESF_PATRIMONIO("Patrimonio"),
        ERI_INGRESO("Ingresos"),
        ERI_COSTO("Costos"),
        ERI_GASTO("Gastos");

        public final String titulo;

        Seccion(String titulo) {
            this.titulo = titulo;
        }

        public boolean esf() {
            return name().startsWith("ESF");
        }
    }

    public record Renglon(String codigo, Seccion seccion, String nombre, List<String> prefijos) {
    }

    /** Cuentas que no van en el formato (de orden, cierre). */
    public static final String NO_APLICA = "NA";
    /** Renglón donde va el resultado del ejercicio calculado. */
    public static final String RESULTADO_EJERCICIO = "T06";

    public static final List<Renglon> TODOS = List.of(
            // ── ESF · Activos ──
            new Renglon("A01", Seccion.ESF_ACTIVO, "Efectivo y equivalentes al efectivo", List.of("11")),
            new Renglon("A02", Seccion.ESF_ACTIVO, "Inversiones e instrumentos financieros derivados", List.of("12")),
            new Renglon("A03", Seccion.ESF_ACTIVO, "Cuentas comerciales por cobrar y otras cuentas por cobrar", List.of("13")),
            new Renglon("A04", Seccion.ESF_ACTIVO, "Activos por impuestos corrientes (anticipos, retenciones, saldos a favor)", List.of("1355")),
            new Renglon("A05", Seccion.ESF_ACTIVO, "Inventarios", List.of("14")),
            new Renglon("A06", Seccion.ESF_ACTIVO, "Propiedades, planta y equipo (neto de depreciación)", List.of("15")),
            new Renglon("A07", Seccion.ESF_ACTIVO, "Activos intangibles", List.of("16")),
            new Renglon("A08", Seccion.ESF_ACTIVO, "Gastos pagados por anticipado y cargos diferidos", List.of("17")),
            new Renglon("A09", Seccion.ESF_ACTIVO, "Otros activos", List.of("18")),
            new Renglon("A10", Seccion.ESF_ACTIVO, "Valorizaciones", List.of("19")),
            // ── ESF · Pasivos ──
            new Renglon("P01", Seccion.ESF_PASIVO, "Obligaciones financieras", List.of("21")),
            new Renglon("P02", Seccion.ESF_PASIVO, "Proveedores", List.of("22")),
            new Renglon("P03", Seccion.ESF_PASIVO, "Cuentas por pagar", List.of("23")),
            new Renglon("P04", Seccion.ESF_PASIVO, "Impuestos, gravámenes y tasas", List.of("24")),
            new Renglon("P05", Seccion.ESF_PASIVO, "Obligaciones laborales", List.of("25")),
            new Renglon("P06", Seccion.ESF_PASIVO, "Pasivos estimados y provisiones", List.of("26")),
            new Renglon("P07", Seccion.ESF_PASIVO, "Ingresos recibidos por anticipado y diferidos", List.of("27")),
            new Renglon("P08", Seccion.ESF_PASIVO, "Otros pasivos", List.of("28", "29")),
            // ── ESF · Patrimonio ──
            new Renglon("T01", Seccion.ESF_PATRIMONIO, "Capital social", List.of("31")),
            new Renglon("T02", Seccion.ESF_PATRIMONIO, "Superávit de capital", List.of("32")),
            new Renglon("T03", Seccion.ESF_PATRIMONIO, "Reservas", List.of("33")),
            new Renglon("T04", Seccion.ESF_PATRIMONIO, "Revalorización del patrimonio", List.of("34")),
            new Renglon("T05", Seccion.ESF_PATRIMONIO, "Dividendos o participaciones decretados en acciones", List.of("35")),
            new Renglon("T06", Seccion.ESF_PATRIMONIO, "Resultado del ejercicio", List.of("36")),
            new Renglon("T07", Seccion.ESF_PATRIMONIO, "Resultados de ejercicios anteriores", List.of("37")),
            new Renglon("T08", Seccion.ESF_PATRIMONIO, "Superávit por valorizaciones", List.of("38")),
            // ── ERI · Ingresos ──
            new Renglon("I01", Seccion.ERI_INGRESO, "Ingresos de actividades ordinarias (netos de devoluciones)", List.of("41")),
            new Renglon("I02", Seccion.ERI_INGRESO, "Ingresos financieros y otros ingresos", List.of("42", "43", "44", "45", "46", "47", "48")),
            // ── ERI · Costos ──
            new Renglon("C01", Seccion.ERI_COSTO, "Costo de ventas", List.of("6")),
            new Renglon("C02", Seccion.ERI_COSTO, "Costos de producción o de operación", List.of("7")),
            // ── ERI · Gastos ──
            new Renglon("G01", Seccion.ERI_GASTO, "Gastos de administración", List.of("51")),
            new Renglon("G02", Seccion.ERI_GASTO, "Gastos de distribución y ventas", List.of("52")),
            new Renglon("G03", Seccion.ERI_GASTO, "Gastos financieros y otros gastos", List.of("53")),
            new Renglon("G04", Seccion.ERI_GASTO, "Impuesto de renta y complementarios", List.of("54")));

    private static final List<String> EXCLUIDOS = List.of("8", "9", "59");

    public static final Map<String, Renglon> POR_CODIGO =
            TODOS.stream().collect(Collectors.toMap(Renglon::codigo, Function.identity()));

    private RenglonesFiscales() {
    }

    /** Renglón por el prefijo más largo; {@link #NO_APLICA} si va fuera; null si no casa con nada. */
    public static String clasificar(String codigoCuenta) {
        if (codigoCuenta == null || codigoCuenta.isBlank()) return null;
        String c = codigoCuenta.trim();
        for (String ex : EXCLUIDOS) {
            if (c.startsWith(ex)) return NO_APLICA;
        }
        String mejor = null;
        int largo = 0;
        for (Renglon r : TODOS) {
            for (String p : r.prefijos()) {
                if (c.startsWith(p) && p.length() > largo) {
                    mejor = r.codigo();
                    largo = p.length();
                }
            }
        }
        return mejor;
    }

    public static boolean existe(String codigo) {
        return NO_APLICA.equals(codigo) || POR_CODIGO.containsKey(codigo);
    }

    /** Clase 1, 5, 6, 7: naturaleza débito. 2, 3, 4: crédito. */
    public static boolean naturalezaDebito(String codigoCuenta) {
        char clase = codigoCuenta.trim().charAt(0);
        return clase == '1' || clase == '5' || clase == '6' || clase == '7';
    }
}
