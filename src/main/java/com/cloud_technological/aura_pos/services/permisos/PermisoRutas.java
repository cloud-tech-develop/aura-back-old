package com.cloud_technological.aura_pos.services.permisos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué submódulo protege cada ruta del API (fase P3 de docs/PLAN_PERMISOS.md).
 *
 * <p>Se busca el prefijo más largo que coincida por segmentos ({@code /api/compras}
 * cubre {@code /api/compras/12} pero no {@code /api/compras-x}). Una ruta puede
 * aceptar varias claves (basta una): así el POS puede vender aunque el perfil no
 * tenga la pantalla de Ventas. {@code modulo.*} = cualquier submódulo del módulo.
 *
 * <p>Una ruta que no está aquí <b>no se bloquea</b>: se anota en el registro como
 * "sin submódulo" para completarla. Al agregar un controller nuevo, agregarlo aquí.
 */
public final class PermisoRutas {

    private static final String POS = "principal.punto-de-venta";

    /** Rutas que no se revisan: autenticación, públicas o del propio usuario. */
    static final List<String> LIBRES = List.of(
            "/api/auth", "/api/public", "/api/platform", "/api/permisos/mios", "/api/municipios",
            "/api/notificaciones", "/api/storage", "/api/documentos");

    /**
     * Maestros que cualquier usuario de la empresa puede LEER: los usan el punto de
     * venta y los formularios (buscar un producto, un cliente, una cuenta…). Escribir
     * en ellos sí exige el submódulo.
     */
    static final List<String> LECTURA_LIBRE = List.of(
            "/api/productos", "/api/terceros", "/api/categorias", "/api/marcas", "/api/unidades-medida",
            "/api/sucursales", "/api/bodegas", "/api/listas-precios", "/api/descuentos", "/api/precios-cliente",
            "/api/descuentos-cliente", "/api/precios-volumen", "/api/calculo-precios", "/api/tesoreria/cuentas",
            "/api/contabilidad/formas-pago", "/api/contabilidad/impuestos", "/api/contabilidad/plan-cuentas",
            "/api/cajas", "/api/turnos", "/api/lotes", "/api/seriales", "/api/inventario", "/api/centros-costos",
            "/api/conceptos-caja", "/api/motivos-merma", "/api/tipos-empleado", "/api/locales",
            "/api/tarifas-retencion", "/api/empresa", "/api/comisiones/tecnicos", "/api/comisiones/vendedores");

    /** Prefijo → claves aceptadas (basta una). */
    static final Map<String, List<String>> CLAVES = construir();

    private PermisoRutas() {
    }

    private static Map<String, List<String>> construir() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // Principal / POS
        m.put("/api/dashboard", List.of("principal.dashboard"));
        // Tableros de inicio por línea de uso (PLAN_PERFIL_EMPRESA): la puerta es el inicio.
        m.put("/api/tablero", List.of("principal.dashboard"));
        m.put("/api/pos/carritos-abandonados/reporte", List.of("reportes.carritos-abandonados"));
        m.put("/api/pos/carritos-abandonados", List.of(POS));
        // Catálogo y precios
        m.put("/api/productos", List.of("catalogo.productos"));
        m.put("/api/productos/composicion", List.of("catalogo.composiciones"));
        m.put("/api/productos/presentaciones", List.of("catalogo.presentaciones"));
        m.put("/api/productos/precios", List.of("precios.precio-productos"));
        m.put("/api/categorias", List.of("catalogo.categorias"));
        m.put("/api/marcas", List.of("catalogo.marcas"));
        m.put("/api/unidades-medida", List.of("catalogo.unidades"));
        m.put("/api/listas-precios", List.of("precios.listas-de-precio"));
        m.put("/api/descuentos", List.of("precios.descuentos"));
        m.put("/api/precios-cliente", List.of("precios.precio-productos"));
        m.put("/api/descuentos-cliente", List.of("precios.descuentos"));
        m.put("/api/precios-volumen", List.of("precios.precio-productos"));
        m.put("/api/calculo-precios", List.of("precios.precio-productos", POS));
        // Inventario
        m.put("/api/inventario", List.of("inventario.stock"));
        m.put("/api/bodegas", List.of("inventario.bodegas"));
        m.put("/api/lotes", List.of("inventario.lotes"));
        m.put("/api/seriales", List.of("inventario.seriales"));
        m.put("/api/kardex", List.of("inventario.kardex"));
        m.put("/api/reconteos", List.of("inventario.reconteos"));
        m.put("/api/mermas", List.of("inventario.mermas"));
        m.put("/api/motivos-merma", List.of("inventario.mermas"));
        m.put("/api/obsequios", List.of("inventario.obsequios"));
        m.put("/api/consumos-internos", List.of("inventario.consumo-interno"));
        m.put("/api/traslados", List.of("inventario.traslados"));
        // Compras
        m.put("/api/compras", List.of("compras.compras"));
        m.put("/api/ordenes-compra", List.of("compras.ordenes-de-compra"));
        m.put("/api/documentos-soporte", List.of("compras.documentos-soporte"));
        // Ventas (el POS vende, cotiza y factura aunque el perfil no tenga la pantalla)
        m.put("/api/ventas", List.of("ventas.ventas", POS));
        m.put("/api/facturas", List.of("ventas.ventas", POS));
        m.put("/api/facturas-venta", List.of("ventas.facturas"));
        m.put("/api/cotizaciones", List.of("ventas.cotizaciones", POS));
        m.put("/api/devoluciones", List.of("ventas.devoluciones"));
        m.put("/api/nota-electronica", List.of("ventas.notas-credito/debito"));
        m.put("/api/terceros", List.of("terceros-y-sucursales.terceros", POS)); // crear cliente desde el POS
        // Cuentas, cartera, tesorería
        m.put("/api/cuentas-cobrar", List.of("cuentas.cuentas-por-cobrar"));
        m.put("/api/cuentas-pagar", List.of("cuentas.cuentas-por-pagar"));
        m.put("/api/cartera", List.of("cartera.cartera"));
        m.put("/api/tesoreria/cuentas", List.of("tesoreria.cuentas-bancarias"));
        m.put("/api/tesoreria/egresos", List.of("tesoreria.egresos"));
        m.put("/api/tesoreria/recaudos", List.of("tesoreria.recaudos"));
        m.put("/api/tesoreria/conciliacion", List.of("tesoreria.conciliacion"));
        m.put("/api/tesoreria", List.of("tesoreria.*"));
        m.put("/api/traslados-fondos", List.of("tesoreria.traslados-de-fondos"));
        m.put("/api/obligaciones-financieras", List.of("tesoreria.obligaciones"));
        // Caja
        m.put("/api/cajas", List.of("caja.cajas"));
        m.put("/api/turnos", List.of("caja.turnos", POS));
        m.put("/api/comprobantes-caja", List.of("caja.comprobantes", POS));
        m.put("/api/caja/supervision-retroactiva", List.of("caja.supervision-de-caja"));
        m.put("/api/usuarios", List.of("caja.usuarios"));
        m.put("/api/perfiles", List.of("caja.perfiles"));
        m.put("/api/bitacora", List.of("caja.bitacora"));
        // Contabilidad
        m.put("/api/contabilidad", List.of("contabilidad.*"));
        m.put("/api/contabilidad/plan-cuentas", List.of("contabilidad.plan-de-cuentas"));
        m.put("/api/contabilidad/asientos", List.of("contabilidad.asientos-contables"));
        m.put("/api/contabilidad/comprobantes", List.of("contabilidad.asientos-contables"));
        m.put("/api/contabilidad/saldos-iniciales", List.of("contabilidad.saldos-iniciales"));
        m.put("/api/contabilidad/balance", List.of("contabilidad.balance-general"));
        m.put("/api/contabilidad/balance-detallado", List.of("contabilidad.balance-de-prueba"));
        m.put("/api/contabilidad/estado-resultados", List.of("contabilidad.estados-financieros"));
        m.put("/api/contabilidad/flujo-caja", List.of("contabilidad.estados-financieros"));
        m.put("/api/contabilidad/libro-mayor", List.of("contabilidad.libros-contables"));
        m.put("/api/contabilidad/libros", List.of("contabilidad.libros-contables"));
        m.put("/api/contabilidad/notas", List.of("contabilidad.notas-contables"));
        m.put("/api/contabilidad/categorias-producto", List.of("contabilidad.categorias-contables"));
        m.put("/api/contabilidad/configuracion-cuentas", List.of("contabilidad.parametrizacion-contable"));
        m.put("/api/contabilidad/formas-pago", List.of("contabilidad.parametrizacion-contable"));
        m.put("/api/contabilidad/impuestos", List.of("contabilidad.parametrizacion-contable"));
        m.put("/api/contabilidad/declaraciones", List.of("contabilidad.declaraciones"));
        m.put("/api/contabilidad/herramientas", List.of("contabilidad.herramientas-del-contador"));
        m.put("/api/reportes-contables", List.of("contabilidad.balance-de-prueba"));
        m.put("/api/activos-fijos", List.of("contabilidad.activos-fijos"));
        m.put("/api/centros-costos", List.of("contabilidad.centros-de-costo"));
        m.put("/api/conceptos-caja", List.of("contabilidad.conceptos-de-caja"));
        m.put("/api/periodos-contables", List.of("contabilidad.periodos-contables"));
        m.put("/api/cierre-contable", List.of("contabilidad.resultados-del-periodo", "contabilidad.cierre-anual"));
        m.put("/api/gastos", List.of("contabilidad.gastos"));
        m.put("/api/importacion", List.of("contabilidad.importar-datos"));
        m.put("/api/tarifas-retencion", List.of("contabilidad.tarifas-retencion"));
        // Recursos humanos
        m.put("/api/recursos-humanos", List.of("recursos-humanos.*"));
        m.put("/api/empleados", List.of("recursos-humanos.empleados"));
        m.put("/api/contrato", List.of("recursos-humanos.empleados"));
        m.put("/api/afiliacion", List.of("recursos-humanos.empleados"));
        m.put("/api/embargo", List.of("recursos-humanos.empleados"));
        m.put("/api/retefuente", List.of("recursos-humanos.empleados"));
        m.put("/api/tipos-empleado", List.of("recursos-humanos.empleados"));
        m.put("/api/certificado", List.of("recursos-humanos.empleados", "recursos-humanos.liquidacion-nomina"));
        m.put("/api/proyectos", List.of("recursos-humanos.proyectos-y-frentes"));
        m.put("/api/frentes", List.of("recursos-humanos.proyectos-y-frentes"));
        m.put("/api/concepto", List.of("recursos-humanos.conceptos"));
        m.put("/api/periodos-nomina", List.of("recursos-humanos.periodos"));
        m.put("/api/nomina", List.of("recursos-humanos.liquidacion-nomina"));
        m.put("/api/proceso", List.of("recursos-humanos.liquidacion-nomina"));
        m.put("/api/nomina/autorizaciones", List.of("recursos-humanos.liquidacion-nomina"));
        m.put("/api/nomina/auditoria", List.of("recursos-humanos.preliquidacion-auditoria"));
        m.put("/api/nomina/config", List.of("recursos-humanos.config-nomina"));
        m.put("/api/nomina-electronica", List.of("recursos-humanos.nomina-electronica"));
        m.put("/api/pila", List.of("recursos-humanos.pila"));
        m.put("/api/prestaciones", List.of("recursos-humanos.prestaciones"));
        m.put("/api/comisiones", List.of("recursos-humanos.comisiones"));
        m.put("/api/comisiones/liquidaciones", List.of("recursos-humanos.liquidar-comisiones"));
        m.put("/api/comisiones/pendientes", List.of("recursos-humanos.liquidar-comisiones"));
        m.put("/api/asistencia", List.of("recursos-humanos.marcaje"));
        m.put("/api/asistencia/frentes", List.of("recursos-humanos.digitacion-asistencia"));
        m.put("/api/asistencia/frente-revision", List.of("recursos-humanos.revision-asistencia-frente",
                "recursos-humanos.preliquidacion-frente"));
        m.put("/api/asistencia/revision", List.of("recursos-humanos.revision-asistencia"));
        m.put("/api/asistencia/novedades", List.of("recursos-humanos.novedades-asistencia"));
        m.put("/api/asistencia/turnos", List.of("recursos-humanos.turnos-empleado"));
        m.put("/api/laboral", List.of("recursos-humanos.configuracion-laboral", "recursos-humanos.calendario-laboral"));
        // Reportes
        m.put("/api/reportes", List.of("reportes.*"));
        m.put("/api/reportes/ventas", List.of("reportes.ventas"));
        m.put("/api/reportes/facturas-electronicas", List.of("reportes.facturacion-electronica"));
        m.put("/api/reportes/notas-electronicas", List.of("reportes.facturacion-electronica"));
        m.put("/api/reportes/kardex", List.of("reportes.movimiento-de-inventario"));
        m.put("/api/reportes/gastos", List.of("reportes.gastos-reporte"));
        m.put("/api/reportes/auditoria", List.of("reportes.reporte-gerencial"));
        m.put("/api/reportes/gerencial", List.of("reportes.reporte-gerencial"));
        m.put("/api/reportes/cartera", List.of("reportes.estado-de-cuenta-reporte"));
        m.put("/api/reportes/inventario", List.of("reportes.inventario"));
        m.put("/api/reportes/avanzado", List.of("reportes.reportes-avanzados"));
        // Vendedores
        m.put("/api/pedidos-vendedor", List.of("vendedores.*", POS));
        m.put("/api/locales", List.of("vendedores.locales"));
        m.put("/api/rutas", List.of("vendedores.rutas"));
        m.put("/api/visitas", List.of("vendedores.visitas", "vendedores.mi-perfil"));
        // Terceros y sucursales
        m.put("/api/sucursales", List.of("terceros-y-sucursales.sucursales"));
        m.put("/api/empresa", List.of("terceros-y-sucursales.sucursales"));
        return m;
    }

    /** Las claves que protegen la ruta, o null si no tiene submódulo asignado. */
    public static List<String> clavesDe(String ruta) {
        return CLAVES.entrySet().stream()
                .filter(e -> coincide(ruta, e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public static boolean esLibre(String ruta) {
        return LIBRES.stream().anyMatch(p -> coincide(ruta, p));
    }

    public static boolean esLecturaLibre(String ruta, AccionPermiso accion) {
        return accion == AccionPermiso.VER && LECTURA_LIBRE.stream().anyMatch(p -> coincide(ruta, p));
    }

    /** Coincidencia por segmentos: el prefijo completo, seguido de nada o de "/". */
    static boolean coincide(String ruta, String prefijo) {
        return ruta.equals(prefijo) || ruta.startsWith(prefijo + "/");
    }

    // ── Acción de la petición ────────────────────────────────────────────────

    /** POST que solo leen: listados, búsquedas, reportes y exportaciones. */
    private static final Set<String> LECTURA = Set.of("page", "search", "buscar", "listar", "excel", "pdf",
            "preview", "previsualizar", "resumen", "calcular", "validar", "consultar", "simular", "sugerencia",
            "sugerencias", "detalle", "documentos", "exportar", "export");
    private static final Set<String> ANULACION = Set.of("anular", "cancelar", "reversar", "revertir", "desactivar");
    private static final Set<String> EDICION = Set.of("aprobar", "rechazar", "pagar", "toggle", "conciliar",
            "conciliar-lote", "contabilizar", "enviar", "activo", "cerrar", "reabrir", "reprocesar");

    /**
     * {@code GET → VER}, {@code DELETE → ANULAR}, {@code PUT/PATCH → EDITAR} (o
     * ANULAR si es una anulación) y {@code POST → CREAR}, salvo los POST que solo
     * leen (VER), anulan (ANULAR) o cambian el estado de algo existente (EDITAR).
     * Todo lo que va a /api/reportes es lectura.
     */
    public static AccionPermiso accionDe(String metodo, String ruta) {
        String m = metodo == null ? "GET" : metodo.toUpperCase();
        if (m.equals("GET") || m.equals("HEAD")) return AccionPermiso.VER;
        if (m.equals("DELETE")) return AccionPermiso.ANULAR;
        if (coincide(ruta, "/api/reportes") || coincide(ruta, "/api/reportes-contables")) return AccionPermiso.VER;

        String[] segmentos = ruta.split("/");
        for (String s : segmentos) {
            if (ANULACION.contains(s)) return AccionPermiso.ANULAR;
        }
        if (m.equals("PUT") || m.equals("PATCH")) return AccionPermiso.EDITAR;
        String ultimo = segmentos.length > 0 ? segmentos[segmentos.length - 1] : "";
        if (LECTURA.contains(ultimo)) return AccionPermiso.VER;
        for (String s : segmentos) {
            if (EDICION.contains(s)) return AccionPermiso.EDITAR;
        }
        return AccionPermiso.CREAR;
    }
}
