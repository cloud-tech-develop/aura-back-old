package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCausacion;
import com.cloud_technological.aura_pos.entity.CausacionEjecucionEntity;
import com.cloud_technological.aura_pos.entity.CausacionProgramadaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.CausacionEjecucionJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.CausacionProgramadaJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta la ejecución de una causación con las líneas de su plantilla. */
@Component
@RequiredArgsConstructor
public class LectorCausacionJpa implements LectorCausacion {

    private final CausacionEjecucionJPARepository ejecucionRepo;
    private final CausacionProgramadaJPARepository causacionRepo;

    @Override
    @Transactional(readOnly = true)
    public CausacionContable cargar(Long ejecucionId, Integer empresaId) {
        CausacionEjecucionEntity ejecucion = ejecucionRepo.findByIdAndEmpresaId(ejecucionId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Ejecución de causación #" + ejecucionId + " no encontrada"));
        CausacionProgramadaEntity causacion = causacionRepo
                .findByIdAndEmpresaId(ejecucion.getCausacionId(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Causación #" + ejecucion.getCausacionId() + " no encontrada"));

        List<LineaCausacion> lineas = causacion.getLineas().stream()
                .map(l -> new LineaCausacion(l.getCuentaId(), l.getDescripcion(),
                        l.getDebito(), l.getCredito(), l.getTerceroId()))
                .toList();
        return new CausacionContable(causacion.getNombre(), ejecucion.getPeriodo(),
                LocalDate.now(), lineas);
    }
}
