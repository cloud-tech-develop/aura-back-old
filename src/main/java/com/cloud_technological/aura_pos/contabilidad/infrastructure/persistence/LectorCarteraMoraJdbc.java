package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCarteraMora;
import com.cloud_technological.aura_pos.repositories.cartera.MoraCastigoQueryRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LectorCarteraMoraJdbc implements LectorCarteraMora {

    private final MoraCastigoQueryRepository query;

    @Override
    public InteresContable interes(Long interesId, Integer empresaId) {
        var i = query.interesContable(interesId, empresaId)
                .orElseThrow(() -> new IllegalStateException("Cobro de intereses #" + interesId + " no encontrado"));
        return new InteresContable(i.numero(), i.fecha(), i.terceroId(), i.terceroNombre(), i.valor());
    }

    @Override
    public CastigoContable castigo(Long castigoId, Integer empresaId) {
        var k = query.castigoContable(castigoId, empresaId)
                .orElseThrow(() -> new IllegalStateException("Castigo de cartera #" + castigoId + " no encontrado"));
        return new CastigoContable(k.numero(), k.fecha(), k.terceroId(), k.numeroCuenta(), k.valor(),
                k.contraProvision(), k.contraGasto());
    }
}
