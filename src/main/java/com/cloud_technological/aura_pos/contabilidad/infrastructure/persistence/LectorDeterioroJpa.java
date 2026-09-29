package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDeterioro;
import com.cloud_technological.aura_pos.entity.DeterioroCalculoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeterioroCalculoJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta la propuesta de deterioro para su asiento en borrador. */
@Component
@RequiredArgsConstructor
public class LectorDeterioroJpa implements LectorDeterioro {

    private final DeterioroCalculoJPARepository calculoRepo;

    @Override
    public DeterioroContable cargar(Long calculoId, Integer empresaId) {
        DeterioroCalculoEntity c = calculoRepo.findByIdAndEmpresaId(calculoId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cálculo de deterioro #" + calculoId + " no encontrado"));
        return new DeterioroContable(c.getFecha(), c.getMonto(), c.getDetalle());
    }
}
