package com.cloud_technological.aura_pos.sync;

import java.util.List;

/**
 * Tablas que bajan de la nube al nodo local, en orden de dependencia.
 *
 * <p>Dos grupos:
 * <ul>
 * <li><b>CATALOGO</b>: lo que la tienda necesita para operar y que se administra
 * en la nube (empresa, usuarios, productos, precios, terceros, plan de cuentas,
 * configuración contable...). Se reemplaza completo en cada bajada: lo que se
 * edite de esto en el nodo se pierde, por eso se edita en la nube.</li>
 * <li><b>SALDO</b>: existencias (inventario, lotes, seriales). El nodo las mueve
 * al vender, así que solo se bajan cuando la cola está vacía, es decir, cuando
 * la nube ya tiene todo lo que pasó en la tienda.</li>
 * </ul>
 *
 * <p>El filtro es SQL fijo con un único parámetro {@code :empresaId}: nunca
 * entra texto del cliente.
 */
public final class SyncTablas {

    public enum Grupo { CATALOGO, SALDO }

    /**
     * Una tabla replicada.
     *
     * @param nombre nombre de la tabla en el esquema public
     * @param filtro condición WHERE que la limita a la empresa (null = tabla global)
     * @param grupo  CATALOGO o SALDO
     */
    public record Tabla(String nombre, String filtro, Grupo grupo) {
    }

    private static final String DE_EMPRESA = "empresa_id = :empresaId";
    /** Algunas tablas traen filas plantilla sin empresa (empresa_id NULL). */
    private static final String DE_EMPRESA_O_GLOBAL = "(empresa_id = :empresaId OR empresa_id IS NULL)";
    private static final String DE_SUS_PRODUCTOS = "producto_id IN (SELECT id FROM producto WHERE empresa_id = :empresaId)";
    private static final String DE_SUS_USUARIOS = "usuario_id IN (SELECT id FROM usuario WHERE empresa_id = :empresaId)";
    private static final String DE_SUS_PERFILES = "perfil_id IN (SELECT id FROM perfil WHERE empresa_id = :empresaId OR empresa_id IS NULL)";

    public static final List<Tabla> TODAS = List.of(
            // Globales
            catalogo("municipios", null),
            catalogo("modulos", null),
            catalogo("submodulos", null),
            catalogo("permiso_accion_especial", null),
            catalogo("entidad_seguridad_social", null),
            catalogo("uvt_valor", null),
            catalogo("unidad_medida", null),
            // Empresa y estructura
            catalogo("empresa", "id = :empresaId"),
            catalogo("empresa_modulo", DE_EMPRESA),
            catalogo("empresa_submodulo", DE_EMPRESA),
            catalogo("sucursal", DE_EMPRESA),
            catalogo("caja", "sucursal_id IN (SELECT id FROM sucursal WHERE empresa_id = :empresaId)"),
            // Seguridad
            catalogo("perfil", DE_EMPRESA_O_GLOBAL),
            catalogo("perfil_permiso", DE_SUS_PERFILES),
            catalogo("perfil_accion_especial", DE_SUS_PERFILES),
            // Terceros (antes que usuario y empleados, que los referencian)
            catalogo("tercero", DE_EMPRESA),
            catalogo("tercero_rol", "tercero_id IN (SELECT id FROM tercero WHERE empresa_id = :empresaId)"),
            catalogo("tercero_credito", DE_EMPRESA),
            catalogo("tipos_empleado", DE_EMPRESA_O_GLOBAL),
            catalogo("empleados", DE_EMPRESA),
            // Solo los usuarios de la empresa: los de plataforma (sin empresa) no bajan.
            catalogo("usuario", DE_EMPRESA),
            catalogo("usuario_sucursal", DE_SUS_USUARIOS),
            catalogo("usuario_permiso", DE_SUS_USUARIOS),
            catalogo("usuario_accion_especial", DE_SUS_USUARIOS),
            catalogo("bodega", DE_EMPRESA),
            // Productos y precios
            catalogo("categoria", DE_EMPRESA),
            catalogo("marca", DE_EMPRESA),
            catalogo("impuesto", DE_EMPRESA_O_GLOBAL),
            catalogo("categoria_contable_producto", DE_EMPRESA_O_GLOBAL),
            catalogo("producto", DE_EMPRESA),
            catalogo("producto_presentacion", DE_SUS_PRODUCTOS),
            catalogo("producto_composicion", "producto_padre_id IN (SELECT id FROM producto WHERE empresa_id = :empresaId)"),
            catalogo("lista_precios", DE_EMPRESA),
            catalogo("producto_precio", DE_SUS_PRODUCTOS),
            catalogo("precio_cliente", DE_EMPRESA),
            catalogo("precio_volumen", DE_EMPRESA),
            catalogo("descuento_cliente", DE_EMPRESA),
            catalogo("regla_descuento", DE_EMPRESA),
            catalogo("regla_credito", DE_EMPRESA_O_GLOBAL),
            catalogo("comision_config", DE_EMPRESA),
            catalogo("motivo_merma", DE_EMPRESA),
            // Cobro y tesorería
            catalogo("condicion_pago", DE_EMPRESA_O_GLOBAL),
            catalogo("concepto_caja", DE_EMPRESA_O_GLOBAL),
            catalogo("cuenta_bancaria", DE_EMPRESA),
            // Contabilidad: lo que el motor necesita para armar el asiento en el nodo
            catalogo("plan_cuenta", DE_EMPRESA_O_GLOBAL),
            catalogo("forma_pago_contable", DE_EMPRESA_O_GLOBAL),
            catalogo("cuenta_config", DE_EMPRESA_O_GLOBAL),
            catalogo("centros_costos", DE_EMPRESA),
            catalogo("periodo_contable", DE_EMPRESA),
            catalogo("tarifa_retencion", DE_EMPRESA_O_GLOBAL),
            // Existencias
            saldo("inventario", "bodega_id IN (SELECT id FROM bodega WHERE empresa_id = :empresaId)"),
            saldo("lote", DE_EMPRESA),
            saldo("serial_producto", DE_EMPRESA));

    private SyncTablas() {
    }

    private static Tabla catalogo(String nombre, String filtro) {
        return new Tabla(nombre, filtro, Grupo.CATALOGO);
    }

    private static Tabla saldo(String nombre, String filtro) {
        return new Tabla(nombre, filtro, Grupo.SALDO);
    }

    /**
     * Tablas de un grupo, en orden.
     *
     * @param grupo grupo pedido
     * @return tablas del grupo
     */
    public static List<Tabla> delGrupo(Grupo grupo) {
        return TODAS.stream().filter(t -> t.grupo() == grupo).toList();
    }
}
