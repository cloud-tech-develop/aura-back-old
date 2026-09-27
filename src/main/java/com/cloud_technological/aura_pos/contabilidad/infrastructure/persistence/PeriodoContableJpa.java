package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.PeriodoContablePort;
import com.cloud_technological.aura_pos.services.implementations.PeriodoContableResolver;

import lombok.RequiredArgsConstructor;

/**
 * Adapter del puerto de períodos: delega en el resolver, que busca el período
 * por la FECHA del asiento, abre el mes si es su primer documento y solo
 * bloquea si ese mes está cerrado (V171).
 */
@Component
@RequiredArgsConstructor
public class PeriodoContableJpa implements PeriodoContablePort {

    private final PeriodoContableResolver resolver;

    @Override
    public Long abiertoPara(Integer empresaId, LocalDate fecha) {
        return resolver.resolverId(empresaId, fecha);
    }
}
