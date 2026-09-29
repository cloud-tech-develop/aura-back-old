package com.cloud_technological.aura_pos.utils;

import com.cloud_technological.aura_pos.entity.VentaEntity;

/**
 * Número de un documento para mostrarlo en movimientos y descripciones.
 *
 * <p>Existe porque las descripciones que la venta deja regadas (kardex, caja,
 * cartera, asientos) venían escritas con el {@code id} de la fila, que es el
 * autoincremental global de la tabla: es compartido por todas las empresas de
 * la base, así que un cliente que apenas empieza a facturar ve su primera
 * venta como "#51605" mientras el POS le dice 80. El número que le pertenece
 * es el consecutivo, que va por sucursal.
 *
 * <p>La regla es la misma que usan el listado ({@code numero_venta} en
 * VentaQueryRepository) y el detalle ({@code numeroVenta} en VentaDto), para
 * que ninguna pantalla contradiga a otra.
 */
public final class Documentos {

    private Documentos() {
    }

    /** Prefijo-consecutivo, o solo el consecutivo si no hay prefijo. */
    public static String numeroVenta(VentaEntity venta) {
        if (venta == null) {
            return "";
        }
        return numeroVenta(venta.getPrefijo(), venta.getConsecutivo(), venta.getId());
    }

    /**
     * Igual que el anterior, para quien no tiene la entity a mano.
     *
     * <p>Si la venta no alcanzó a tener consecutivo cae al {@code #id}: sin eso
     * la descripción quedaría sin ninguna forma de rastrear el documento.
     */
    public static String numeroVenta(String prefijo, Long consecutivo, Long id) {
        if (consecutivo == null) {
            return id != null ? "#" + id : "";
        }
        return (prefijo != null && !prefijo.isBlank())
                ? prefijo.trim() + "-" + consecutivo
                : String.valueOf(consecutivo);
    }
}
