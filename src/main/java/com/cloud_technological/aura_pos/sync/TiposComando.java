package com.cloud_technological.aura_pos.sync;

/** Operaciones del nodo que la nube sabe reproducir. */
public final class TiposComando {

    public static final String TERCERO_CREAR = "TERCERO_CREAR";
    public static final String TURNO_ABRIR = "TURNO_ABRIR";
    public static final String TURNO_CERRAR = "TURNO_CERRAR";
    public static final String MOVIMIENTO_CAJA = "MOVIMIENTO_CAJA";
    public static final String VENTA_CREAR = "VENTA_CREAR";
    public static final String VENTA_ANULAR = "VENTA_ANULAR";
    /** Factura de Facturación (ERP): crea y emite la factura y su venta. */
    public static final String FACTURA_EMITIR = "FACTURA_EMITIR";
    /** Factura electrónica de una venta ya sincronizada. */
    public static final String FE_EMITIR = "FE_EMITIR";

    /** Tablas de {@code sync_id_map}. */
    public static final String T_TERCERO = "tercero";
    public static final String T_TURNO = "turno_caja";
    public static final String T_VENTA = "venta";
    public static final String T_FACTURA = "factura_venta";
    public static final String T_MOVIMIENTO_CAJA = "movimiento_caja";

    private TiposComando() {
    }
}
