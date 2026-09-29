package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDiferenciaCaja;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta el turno cerrado (fecha de cierre + diferencia contada). */
@Component
@RequiredArgsConstructor
public class LectorDiferenciaCajaJpa implements LectorDiferenciaCaja {

    private final TurnoCajaJPARepository turnoRepo;

    @Override
    public DiferenciaCaja cargar(Long turnoId, Integer empresaId) {
        TurnoCajaEntity turno = turnoRepo.findByIdAndCajaSucursalEmpresaId(turnoId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Turno de caja #" + turnoId + " no encontrado para contabilizar"));
        LocalDate fecha = turno.getFechaCierre() != null
                ? turno.getFechaCierre().toLocalDate() : LocalDate.now();
        return new DiferenciaCaja(fecha, turno.getDiferencia());
    }
}
