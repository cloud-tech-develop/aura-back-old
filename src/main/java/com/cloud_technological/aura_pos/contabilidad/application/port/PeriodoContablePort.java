package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.time.LocalDate;

import com.cloud_technological.aura_pos.contabilidad.application.exception.PeriodoCerradoException;

/**
 * Puerto de períodos contables: entrega el período abierto donde puede
 * registrarse un asiento con la fecha dada.
 */
public interface PeriodoContablePort {

    /**
     * Id del período ABIERTO de la empresa para la fecha.
     *
     * @throws PeriodoCerradoException si el período de esa fecha está cerrado
     */
    Long abiertoPara(Integer empresaId, LocalDate fecha);
}
