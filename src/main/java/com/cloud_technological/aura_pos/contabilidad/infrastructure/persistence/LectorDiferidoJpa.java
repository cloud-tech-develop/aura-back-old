package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDiferido;
import com.cloud_technological.aura_pos.entity.DiferidoAmortizacionEntity;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoAmortizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta la cuota de amortización con los datos contables del gasto. */
@Component
@RequiredArgsConstructor
public class LectorDiferidoJpa implements LectorDiferido {

    private final DiferidoAmortizacionJPARepository amortizacionRepo;
    private final GastoJPARepository gastoRepo;

    @Override
    public CuotaDiferido cargar(Long amortizacionId, Integer empresaId) {
        DiferidoAmortizacionEntity cuota = amortizacionRepo
                .findByIdAndEmpresaId(amortizacionId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Amortización #" + amortizacionId + " no encontrada para contabilizar"));
        GastoEntity gasto = gastoRepo.findByIdAndEmpresaId(cuota.getGastoId(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Gasto #" + cuota.getGastoId() + " del diferido no encontrado"));
        return new CuotaDiferido(gasto.getId(), cuota.getPeriodo(), LocalDate.now(),
                cuota.getMonto(), gasto.getCuentaContableId(), gasto.getTerceroId(),
                gasto.getCentroCostoId());
    }
}
