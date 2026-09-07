package com.cloud_technological.aura_pos.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo único de los movimientos que puede tener el kardex.
 *
 * <p>Existía repartido en literales sueltos: cada servicio escribía su
 * {@code "MERMA"} o su {@code "OBSEQUIO"} a mano en {@code tipo_movimiento}, y
 * el front mantenía su propia lista para el filtro. Las dos listas se
 * separaron — el front conocía 9 tipos y el backend escribía 17 — así que
 * merma, obsequio, devolución y reconteo se veían en el listado pero no se
 * podían filtrar. Este enum es ahora la única fuente y se expone por API.
 *
 * <h2>El grupo es informativo, no la fuente de la verdad</h2>
 *
 * <p>Sirve para agrupar el filtro en la pantalla. <b>No</b> se debe usar para
 * decidir si un movimiento suma o resta: la mayoría de orígenes guarda la
 * salida con {@code cantidad} negada, pero el reconteo guarda el valor absoluto
 * y distingue el sentido en el nombre del tipo. El único dato fiable es
 * {@code saldo_nuevo - saldo_anterior}, que está en todas las filas y no
 * depende de quién las escribió.
 */
public enum TipoMovimientoInventario {

    // ── Entradas ────────────────────────────────────────────────────────
    COMPRA("Compra", Grupo.ENTRADA, Familia.COMPRAS),
    EDICION_COMPRA("Edición de compra", Grupo.ENTRADA, Familia.COMPRAS),
    DEVOLUCION("Devolución de cliente", Grupo.ENTRADA, Familia.DEVOLUCIONES),
    TRASLADO_ENTRADA("Traslado — entrada", Grupo.ENTRADA, Familia.TRASLADOS),
    ANULACION_VENTA("Anulación de venta", Grupo.ENTRADA, Familia.ANULACIONES),
    ANULACION_MERMA("Anulación de merma", Grupo.ENTRADA, Familia.ANULACIONES),
    ANULACION_OBSEQUIO("Anulación de obsequio", Grupo.ENTRADA, Familia.ANULACIONES),
    RECONTEO_AJUSTE_POSITIVO("Reconteo — sobrante", Grupo.ENTRADA, Familia.RECONTEOS),

    // ── Salidas ─────────────────────────────────────────────────────────
    VENTA("Venta", Grupo.SALIDA, Familia.VENTAS),
    MERMA("Merma", Grupo.SALIDA, Familia.MERMAS),
    OBSEQUIO("Obsequio", Grupo.SALIDA, Familia.OBSEQUIOS),
    TRASLADO_SALIDA("Traslado — salida", Grupo.SALIDA, Familia.TRASLADOS),
    DEVOLUCION_CAMBIO("Cambio en devolución", Grupo.SALIDA, Familia.DEVOLUCIONES),
    NOTA_CREDITO_COMPRA("Nota crédito a proveedor", Grupo.SALIDA, Familia.COMPRAS),
    ANULACION_COMPRA("Anulación de compra", Grupo.SALIDA, Familia.ANULACIONES),
    ANULACION_DEVOLUCION("Anulación de devolución", Grupo.SALIDA, Familia.ANULACIONES),
    EDICION_COMPRA_REVERSION("Reverso de edición de compra", Grupo.SALIDA, Familia.COMPRAS),
    RECONTEO_AJUSTE_NEGATIVO("Reconteo — faltante", Grupo.SALIDA, Familia.RECONTEOS),

    // ── Mixtos ──────────────────────────────────────────────────────────
    /** Genera dos filas: entra en la sucursal origen y sale de la destino. */
    ANULACION_TRASLADO("Anulación de traslado", Grupo.MIXTO, Familia.ANULACIONES);

    /** Si el movimiento suma o resta stock. Solo para agrupar el filtro. */
    public enum Grupo { ENTRADA, SALIDA, MIXTO }

    /** De qué operación viene. Es el desglose por columnas del reporte. */
    public enum Familia {
        COMPRAS, VENTAS, DEVOLUCIONES, MERMAS, OBSEQUIOS,
        TRASLADOS, ANULACIONES, RECONTEOS
    }

    private final String etiqueta;
    private final Grupo grupo;
    private final Familia familia;

    TipoMovimientoInventario(String etiqueta, Grupo grupo, Familia familia) {
        this.etiqueta = etiqueta;
        this.grupo = grupo;
        this.familia = familia;
    }

    /** Lo que se guarda en {@code movimiento_inventario.tipo_movimiento}. */
    public String codigo() {
        return name();
    }

    public String etiqueta() {
        return etiqueta;
    }

    public Grupo grupo() {
        return grupo;
    }

    public Familia familia() {
        return familia;
    }

    /** Los códigos de una familia, para armar el desglose del reporte. */
    public static List<String> codigosDe(Familia familia) {
        return Arrays.stream(values())
                .filter(t -> t.familia == familia)
                .map(TipoMovimientoInventario::codigo)
                .toList();
    }

    /** Los códigos de un grupo, para traducir el filtro a la consulta. */
    public static List<String> codigosDe(Grupo grupo) {
        return Arrays.stream(values())
                .filter(t -> t.grupo == grupo)
                .map(TipoMovimientoInventario::codigo)
                .toList();
    }

    /**
     * Vacío si el código no está en el catálogo. Se devuelve vacío en vez de
     * fallar porque en la tabla puede haber tipos de versiones anteriores: el
     * reporte los muestra con su código crudo antes que esconder la fila.
     */
    public static Optional<TipoMovimientoInventario> de(String codigo) {
        if (codigo == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    /** La etiqueta legible, o el código crudo si no se conoce el tipo. */
    public static String etiquetaDe(String codigo) {
        return de(codigo).map(TipoMovimientoInventario::etiqueta).orElse(codigo);
    }
}
