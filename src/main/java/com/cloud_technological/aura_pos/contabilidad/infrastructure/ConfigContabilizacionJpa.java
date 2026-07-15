package com.cloud_technological.aura_pos.contabilidad.infrastructure;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.ConfigContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.domain.model.EstadoAsiento;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;

import lombok.RequiredArgsConstructor;

/** Lee el modo de contabilización desde la empresa (V88). */
@Component
@RequiredArgsConstructor
public class ConfigContabilizacionJpa implements ConfigContabilizacion {

    private final EmpresaJPARepository empresaRepo;

    @Override
    public EstadoAsiento estadoInicial(Integer empresaId) {
        return empresaRepo.findById(empresaId)
                .map(e -> "REVISION".equalsIgnoreCase(e.getModoContabilizacion())
                        ? EstadoAsiento.BORRADOR
                        : EstadoAsiento.CONTABILIZADO)
                .orElse(EstadoAsiento.CONTABILIZADO);
    }
}
