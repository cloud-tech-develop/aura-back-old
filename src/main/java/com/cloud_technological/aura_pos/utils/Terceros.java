package com.cloud_technological.aura_pos.utils;

import com.cloud_technological.aura_pos.entity.TerceroEntity;

/**
 * Nombre de un tercero para mostrarlo en documentos y movimientos.
 *
 * <p>Existe porque el nombre está repartido en tres generaciones de columnas:
 * `razon_social` para empresas, `nombre1/apellido1` desagregado (V97, lo que
 * exigen DIAN y UGPP) y los viejos `nombres/apellidos`, ya deprecados pero aún
 * con datos. Cada módulo lo armaba a su manera y algunos usaban solo los
 * deprecados, así que un tercero migrado salía en blanco.
 */
public final class Terceros {

    private Terceros() {
    }

    /** Razón social si es empresa; si no, el nombre desagregado. Nunca null. */
    @SuppressWarnings("deprecation")
    public static String nombreVisible(TerceroEntity tercero) {
        if (tercero == null) {
            return "";
        }
        if (esUtil(tercero.getRazonSocial())) {
            return tercero.getRazonSocial().trim();
        }
        String desagregado = unir(tercero.getNombre1(), tercero.getNombre2(),
                tercero.getApellido1(), tercero.getApellido2());
        if (esUtil(desagregado)) {
            return desagregado;
        }
        return unir(tercero.getNombres(), tercero.getApellidos());
    }

    private static String unir(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String parte : partes) {
            if (esUtil(parte)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(parte.trim());
            }
        }
        return sb.toString();
    }

    private static boolean esUtil(String valor) {
        return valor != null && !valor.isBlank();
    }
}
