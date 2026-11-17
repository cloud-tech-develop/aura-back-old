package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Una novedad pasa un tope que se resuelve AUTORIZÁNDOLA (horas extra o recargos
 * por encima del tope del cargo, o un tope de la vinculación "con autorización").
 *
 * <p>Al liquidar es un error más (400). Al registrar la novedad no lo es: quien la
 * agrega la deja guardada "por autorizar" en vez de rechazarla.
 */
public class TopeNovedadException extends GlobalException {

    public TopeNovedadException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
