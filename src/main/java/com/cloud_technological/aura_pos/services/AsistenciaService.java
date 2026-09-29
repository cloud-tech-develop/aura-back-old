package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia.AsistenciaDiaDto;
import com.cloud_technological.aura_pos.dto.asistencia.CreateMarcajeDto;
import com.cloud_technological.aura_pos.dto.asistencia.MarcajeDto;

public interface AsistenciaService {

    // Marcajes
    MarcajeDto registrarMarcaje(CreateMarcajeDto dto, Integer empresaId, Long usuarioId);
    List<MarcajeDto> listarMarcajes(Long empleadoId, LocalDate fecha, Integer empresaId);
    void anularMarcaje(Long id, Integer empresaId);

    // Consolidación diaria
    AsistenciaDiaDto consolidarDia(Long empleadoId, LocalDate fecha, Integer empresaId);
    List<AsistenciaDiaDto> consolidarRango(LocalDate desde, LocalDate hasta, Integer empresaId);
    List<AsistenciaDiaDto> listarDias(Long empleadoId, LocalDate desde, LocalDate hasta, Integer empresaId);

    // Aprobación del día consolidado
    AsistenciaDiaDto aprobarDia(Long diaId, Integer empresaId, Long usuarioId);
    AsistenciaDiaDto rechazarDia(Long diaId, String observacion, Integer empresaId, Long usuarioId);
}
