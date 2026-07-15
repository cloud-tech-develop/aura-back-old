package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.exception.PeriodoCerradoException;
import com.cloud_technological.aura_pos.contabilidad.application.port.PeriodoContablePort;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter del puerto de períodos sobre el modelo actual: un único período
 * ABIERTO por empresa (la fecha del asiento aún no restringe; cuando el
 * modelo soporte varios períodos, este adapter filtra por rango sin tocar
 * el caso de uso).
 */
@Component
@RequiredArgsConstructor
public class PeriodoContableJpa implements PeriodoContablePort {

    private final PeriodoContableJPARepository periodoRepo;

    @Override
    public Long abiertoPara(Integer empresaId, LocalDate fecha) {
        return periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new PeriodoCerradoException(
                        "No hay un período contable ABIERTO. Abra un período antes de generar asientos."))
                .getId();
    }
}
