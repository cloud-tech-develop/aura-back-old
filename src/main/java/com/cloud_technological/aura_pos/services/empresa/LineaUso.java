package com.cloud_technological.aura_pos.services.empresa;

import java.util.List;
import java.util.Optional;

/**
 * Para qué usa la empresa a Aura (docs/PLAN_PERFIL_EMPRESA.md). Se declara en
 * {@code empresa.configuracion}; no se deduce de los submódulos.
 *
 * <p>Las líneas NO dan permisos (eso sigue en submódulos + perfiles). Deciden la
 * plantilla de módulos al crear la empresa, qué se siembra al arrancar y qué
 * tablero ve al entrar. El orden del enum es la prioridad del tablero de inicio.
 *
 * <p>Las claves son {@code modulo.submodulo} normalizadas (minúsculas, sin tildes,
 * espacios → guion); {@code modulo.*} toma el módulo entero.
 */
public enum LineaUso {

    POS("Punto de venta",
            "Tienda, restaurante o mostrador: vende por caja con inventario.",
            List.of("principal.*", "catalogo.*", "precios.*", "inventario.*", "compras.*", "ventas.*",
                    "cuentas.*", "cartera.*", "tesoreria.*", "caja.*", "contabilidad.*", "reportes.*"),
            List.of("principal.punto-de-venta")),

    COMERCIAL("Comercial (ERP)",
            "Factura, compra y maneja inventario sin mostrador.",
            List.of("catalogo.*", "precios.*", "inventario.*", "compras.*", "ventas.facturas",
                    "ventas.notas-credito/debito", "ventas.cotizaciones", "ventas.devoluciones", "cuentas.*",
                    "cartera.*", "tesoreria.*", "caja.comprobantes", "contabilidad.*", "vendedores.*", "reportes.*"),
            List.of("ventas.facturas")),

    CONTABILIDAD("Contabilidad",
            "El contador lleva la contabilidad directamente en Aura.",
            List.of("contabilidad.*", "tesoreria.*", "cartera.*", "cuentas.*", "compras.documentos-soporte",
                    "caja.comprobantes", "reportes.estado-de-cuenta-reporte", "reportes.gastos-reporte"),
            List.of("contabilidad.plan-de-cuentas", "contabilidad.notas-contables")),

    NOMINA("Nómina",
            "Recursos humanos: empleados, nómina, nómina electrónica y PILA.",
            List.of("recursos-humanos.*"),
            List.of("recursos-humanos.empleados", "recursos-humanos.liquidacion-nomina"));

    /** Lo que toda empresa necesita, sea cual sea la línea. */
    public static final List<String> BASE = List.of(
            "principal.dashboard", "caja.usuarios", "caja.perfiles", "caja.bitacora", "terceros-y-sucursales.*");

    /** Línea que se asume cuando la empresa no ha declarado ninguna (comportamiento histórico). */
    public static final LineaUso POR_DEFECTO = POS;

    private final String nombre;
    private final String descripcion;
    private final List<String> plantilla;
    private final List<String> minimos;

    LineaUso(String nombre, String descripcion, List<String> plantilla, List<String> minimos) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.plantilla = plantilla;
        this.minimos = minimos;
    }

    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    /** Claves que vienen marcadas en el árbol de módulos (sin {@link #BASE}). */
    public List<String> getPlantilla() { return plantilla; }
    /** Pantallas sin las cuales la línea no tiene sentido: se exigen al guardar. */
    public List<String> getMinimos() { return minimos; }

    /**
     * Pasos de arranque que necesita. Todas contabilizan (ventas, compras, nómina
     * generan asientos), así que todas piden el PUC y la configuración contable.
     * Nómina usa los conceptos globales: no siembra nada propio.
     */
    public List<PasoArranque> getPasos() {
        return List.of(PasoArranque.CONTABLE);
    }

    public static Optional<LineaUso> de(String codigo) {
        if (codigo == null) return Optional.empty();
        try {
            return Optional.of(valueOf(codigo.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
