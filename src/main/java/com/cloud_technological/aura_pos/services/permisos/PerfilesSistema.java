package com.cloud_technological.aura_pos.services.permisos;

import java.util.List;

/**
 * Perfiles de sistema que toda empresa tiene, calcados de lo que cada rol veía
 * en el menú antes de existir los perfiles.
 *
 * <p>La migración V190 los crea para las empresas que ya existían; esta clase los
 * crea para las que lleguen después ({@link PerfilesSistemaService}). <b>Si se
 * cambia uno, cambiar el otro.</b>
 *
 * <p>Las claves son {@code modulo.submodulo} (códigos de las tablas modulos y submodulos).
 */
public final class PerfilesSistema {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String CAJERO = "CAJERO";
    public static final String VENDEDOR = "VENDEDOR";
    public static final String SUPERVISOR = "SUPERVISOR";
    /** Roles que no son uno de los fijos (cargo del empleado guardado como rol). */
    public static final String BASICO = "BASICO";
    /**
     * Con el que entran los usuarios de una firma contable al cliente (V199). No está
     * en {@link #TODOS}: solo se crea en las empresas que tienen firma.
     */
    public static final String CONTADOR_EXTERNO = "CONTADOR_EXTERNO";

    public record Definicion(String codigo, String nombre, String descripcion, boolean accesoTotal,
            List<String> claves) {
    }

    public static final List<Definicion> TODOS = List.of(
            new Definicion(ADMINISTRADOR, "Administrador", "Todo lo que la empresa tiene activo", true, List.of()),
            new Definicion(CAJERO, "Cajero",
                    "Punto de venta, ventas, cotizaciones, devoluciones, comprobantes y turnos", false,
                    List.of("principal.punto-de-venta", "ventas.ventas", "ventas.cotizaciones",
                            "ventas.devoluciones", "caja.comprobantes", "caja.turnos")),
            new Definicion(VENDEDOR, "Vendedor", "Punto de venta, mi perfil, escanear QR y turnos", false,
                    List.of("principal.punto-de-venta", "vendedores.mi-perfil", "vendedores.escanear-qr",
                            "caja.turnos")),
            new Definicion(SUPERVISOR, "Supervisor", "Punto de venta y turnos", false,
                    List.of("principal.punto-de-venta", "caja.turnos")),
            new Definicion(BASICO, "Básico", "Punto de venta y turnos (roles que no son uno de los fijos)", false,
                    List.of("principal.punto-de-venta", "caja.turnos")));

    /**
     * Contabilidad, compras (facturas de compra, gastos, documento soporte), cartera,
     * CxC/CxP, tesorería, terceros y reportes. Sin ventas, POS ni caja: las facturas de
     * venta las activa el administrador del cliente editando este perfil.
     * {@code modulo.*} = el módulo entero.
     */
    public static final Definicion CONTADOR = new Definicion(CONTADOR_EXTERNO, "Contador externo",
            "Firma contable: contabilidad, compras, cartera, tesorería y reportes. Sin ventas, POS ni caja", false,
            List.of("principal.dashboard", "contabilidad.*", "compras.*", "cuentas.*", "cartera.*", "tesoreria.*",
                    "reportes.*", "terceros-y-sucursales.terceros"));

    private PerfilesSistema() {
    }

    /**
     * Perfil de sistema que corresponde a un rol; null para PLATFORM_ADMIN. Un
     * rol que no es de los fijos (el cargo de un empleado) cae en BASICO, que es
     * lo que el menú le mostraba.
     */
    public static String codigoPorRol(String rol) {
        if (rol == null || rol.isBlank()) return null;
        return switch (rol.trim().toUpperCase()) {
            case "PLATFORM_ADMIN" -> null;
            case "SUPER_ADMIN", "ADMIN" -> ADMINISTRADOR;
            case "CAJERO" -> CAJERO;
            case "VENDEDOR" -> VENDEDOR;
            case "SUPERVISOR" -> SUPERVISOR;
            default -> BASICO;
        };
    }
}
