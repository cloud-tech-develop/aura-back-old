package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAnticipos;
import com.cloud_technological.aura_pos.entity.AnticipoCruceEntity;
import com.cloud_technological.aura_pos.entity.AnticipoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AnticipoCruceJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AnticipoJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta anticipos y cruces al snapshot de los generadores AN/AC. */
@Component
@RequiredArgsConstructor
public class LectorAnticiposJpa implements LectorAnticipos {

    private final AnticipoJPARepository anticipoRepo;
    private final AnticipoCruceJPARepository cruceRepo;

    @Override
    public AnticipoContable cargar(Long anticipoId, Integer empresaId) {
        AnticipoEntity a = anticipoRepo.findByIdAndEmpresaId(anticipoId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Anticipo #" + anticipoId + " no encontrado para contabilizar"));
        return new AnticipoContable(a.getTipo(), a.getFecha(), a.getMonto(),
                a.getTerceroId(), a.getMetodoPago(), a.getCuentaBancariaId());
    }

    @Override
    public CruceContable cargarCruce(Long cruceId, Integer empresaId) {
        AnticipoCruceEntity c = cruceRepo.findByIdAndEmpresaId(cruceId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cruce de anticipo #" + cruceId + " no encontrado para contabilizar"));
        AnticipoEntity a = anticipoRepo.findByIdAndEmpresaId(c.getAnticipoId(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Anticipo #" + c.getAnticipoId() + " del cruce no encontrado"));
        return new CruceContable(a.getTipo(), c.getFecha(), c.getMonto(), a.getTerceroId());
    }
}
