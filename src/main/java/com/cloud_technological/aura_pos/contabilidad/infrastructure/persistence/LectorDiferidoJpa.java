package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDiferido;
import com.cloud_technological.aura_pos.entity.DiferidoAmortizacionEntity;
import com.cloud_technological.aura_pos.entity.DiferidoEntity;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoAmortizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta la cuota de amortización con los datos contables de su origen. */
@Component
@RequiredArgsConstructor
public class LectorDiferidoJpa implements LectorDiferido {

    private final DiferidoAmortizacionJPARepository amortizacionRepo;
    private final GastoJPARepository gastoRepo;
    private final DiferidoJPARepository diferidoRepo;

    @Override
    public CuotaDiferido cargar(Long amortizacionId, Integer empresaId) {
        DiferidoAmortizacionEntity cuota = amortizacionRepo
                .findByIdAndEmpresaId(amortizacionId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Amortización #" + amortizacionId + " no encontrada para contabilizar"));
        LocalDate fecha = fechaDelPeriodo(cuota.getPeriodo());

        if (cuota.getDiferidoId() != null) {
            DiferidoEntity diferido = diferidoRepo.findById(cuota.getDiferidoId())
                    .filter(d -> empresaId.equals(d.getEmpresaId()))
                    .orElseThrow(() -> new IllegalStateException(
                            "Diferido #" + cuota.getDiferidoId() + " no encontrado"));
            return new CuotaDiferido(
                    diferido.getOrigenTipo().toLowerCase() + " #" + diferido.getOrigenId()
                            + " — " + diferido.getDescripcion(),
                    cuota.getPeriodo(), fecha, cuota.getMonto(), diferido.getCuentaGastoId(),
                    diferido.getCuentaDiferidoId(), diferido.getTerceroId(), diferido.getCentroCostoId());
        }

        GastoEntity gasto = gastoRepo.findByIdAndEmpresaId(cuota.getGastoId(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Gasto #" + cuota.getGastoId() + " del diferido no encontrado"));
        return new CuotaDiferido("gasto #" + gasto.getId(), cuota.getPeriodo(), fecha,
                cuota.getMonto(), gasto.getCuentaContableId(), null, gasto.getTerceroId(),
                gasto.getCentroCostoId());
    }

    /**
     * La cuota es del mes que amortiza: se fecha su último día (o hoy, si el
     * mes está en curso). Con la fecha de hoy, correr la amortización atrasada
     * de un mes anterior la dejaba en otro período.
     */
    private static LocalDate fechaDelPeriodo(String periodo) {
        LocalDate hoy = LocalDate.now();
        try {
            LocalDate fin = YearMonth.parse(periodo).atEndOfMonth();
            return fin.isAfter(hoy) ? hoy : fin;
        } catch (RuntimeException e) {
            return hoy;
        }
    }
}
