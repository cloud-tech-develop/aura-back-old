package com.cloud_technological.aura_pos.dto.auditoria;

/**
 * Qué tan grave es un hallazgo.
 *
 * <p>La severidad <b>no depende del monto</b>. Un asiento descuadrado de $1.000
 * es ALTA porque la contabilidad no cierra; un gasto sin soporte de $5.000.000
 * es MEDIA porque la plata está y se sabe dónde, lo que falta es el papel. El
 * monto sirve para priorizar <i>dentro</i> de un mismo nivel, no para definirlo.
 */
public enum Severidad {
    /** Plata que no se puede explicar, o contabilidad que no cierra. */
    ALTA,
    /** Cuadra, pero no se puede defender ante un tercero. */
    MEDIA,
    /** Higiene de datos: no compromete cifras. */
    BAJA
}
