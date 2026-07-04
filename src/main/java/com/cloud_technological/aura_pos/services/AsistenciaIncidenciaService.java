package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia.CrearIncidenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.IncidenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.RevisarIncidenciaDto;

public interface AsistenciaIncidenciaService {
    List<IncidenciaDto> generarDesdeDia(Long empleadoId, LocalDate fecha, Integer empresaId);
    List<IncidenciaDto> listar(Long empleadoId, LocalDate desde, LocalDate hasta, Integer empresaId);
    IncidenciaDto crearManual(CrearIncidenciaDto dto, Integer empresaId, Long usuarioId);
    IncidenciaDto revisar(Long id, RevisarIncidenciaDto dto, Integer empresaId, Long usuarioId);
}
