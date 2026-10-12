package com.cloud_technological.aura_pos.services.firma_contable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lo que una firma puede activarle a un cliente gestionado (docs/PLAN_FIRMA_CONTABLE.md §9).
 * Es una lista cerrada: nunca punto de venta, caja ni inventario. Las claves son
 * {@code modulo.submodulo}; {@code modulo.*} = el módulo entero.
 */
public enum PaqueteGestionado {

    CONTABILIDAD("Contabilidad",
            "Plan de cuentas, asientos, notas, cierre, conciliación, libros, estados financieros, tesorería, cartera y reportes",
            List.of("contabilidad.*", "tesoreria.*", "cuentas.*", "cartera.*", "reportes.*", "caja.comprobantes")),

    FACTURACION("Facturación de venta",
            "Facturas (electrónicas), notas crédito y débito, cotizaciones y productos o servicios",
            List.of("ventas.facturas", "ventas.notas-credito/debito", "ventas.cotizaciones", "catalogo.*")),

    COMPRAS("Compras y gastos",
            "Facturas de compra, gastos, documento soporte y órdenes de compra",
            List.of("compras.*")),

    NOMINA("Nómina",
            "Empleados, liquidación de nómina, nómina electrónica y PILA",
            List.of("recursos-humanos.*"));

    /** Lo que todo gestionado tiene: inicio, usuarios, perfiles, bitácora, terceros y sedes. */
    public static final List<String> BASE = List.of(
            "principal.dashboard", "caja.usuarios", "caja.perfiles", "caja.bitacora", "terceros-y-sucursales.*");

    private final String nombre;
    private final String descripcion;
    private final List<String> claves;

    PaqueteGestionado(String nombre, String descripcion, List<String> claves) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.claves = claves;
    }

    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public List<String> getClaves() { return claves; }

    public static Optional<PaqueteGestionado> de(String codigo) {
        if (codigo == null) return Optional.empty();
        try {
            return Optional.of(valueOf(codigo.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Claves de los paquetes elegidos más la base; Contabilidad siempre va. */
    public static List<String> claves(List<PaqueteGestionado> paquetes) {
        List<String> out = new ArrayList<>(BASE);
        out.addAll(CONTABILIDAD.claves);
        for (PaqueteGestionado p : paquetes) {
            if (p != CONTABILIDAD) out.addAll(p.claves);
        }
        return out;
    }

    /** Todas las claves posibles de un gestionado (para el perfil de consulta). */
    public static List<String> todasLasClaves() {
        return claves(List.of(values()));
    }
}
