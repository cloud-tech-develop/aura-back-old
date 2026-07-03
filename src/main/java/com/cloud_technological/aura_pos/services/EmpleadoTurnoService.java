package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia.CreateEmpleadoTurnoDto;
import com.cloud_technological.aura_pos.dto.asistencia.EmpleadoTurnoDto;

public interface EmpleadoTurnoService {
    List<EmpleadoTurnoDto> listarPorEmpleado(Long empleadoId, Integer empresaId);
    EmpleadoTurnoDto asignar(CreateEmpleadoTurnoDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}
