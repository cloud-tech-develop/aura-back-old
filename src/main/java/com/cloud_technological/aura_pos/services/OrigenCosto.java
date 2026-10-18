package com.cloud_technological.aura_pos.services;

/**
 * Qué documento movió el costo de un producto. Se guarda en el historial de
 * costos (V226) para que cada cambio diga de dónde salió.
 *
 * @param tipo            COMPRA, NOTA_CREDITO_COMPRA, ANULACION_COMPRA…
 * @param documentoId     id del documento, si lo hay
 * @param documentoNumero número visible del documento
 * @param terceroId       proveedor, cuando aplica
 * @param observacion     texto libre corto
 */
public record OrigenCosto(String tipo, Long documentoId, String documentoNumero, Long terceroId,
        String observacion) {

    public static final String COMPRA = "COMPRA";
    public static final String NOTA_CREDITO_COMPRA = "NOTA_CREDITO_COMPRA";
    public static final String ANULACION_COMPRA = "ANULACION_COMPRA";
    public static final String ANULACION_NOTA_CREDITO = "ANULACION_NOTA_CREDITO";
    public static final String EDICION_COMPRA_REVERSION = "EDICION_COMPRA_REVERSION";
    public static final String EDICION_COMPRA = "EDICION_COMPRA";
    public static final String SALDO_INICIAL = "SALDO_INICIAL";
    public static final String CREACION = "CREACION";
    public static final String AJUSTE_MANUAL = "AJUSTE_MANUAL";
    public static final String RECETA = "RECETA";
    public static final String IMPORTACION = "IMPORTACION";

    /** Origen sin documento (ajuste manual, receta, importación). */
    public static OrigenCosto de(String tipo, String observacion) {
        return new OrigenCosto(tipo, null, null, null, observacion);
    }
}
