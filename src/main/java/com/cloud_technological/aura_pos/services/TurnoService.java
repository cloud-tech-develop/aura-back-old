package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia.CreateTurnoDto;
import com.cloud_technological.aura_pos.dto.asistencia.TurnoDto;

public interface TurnoService {
    List<TurnoDto> listar(Integer empresaId, boolean soloActivos);
    TurnoDto obtener(Long id, Integer empresaId);
    TurnoDto crear(CreateTurnoDto dto, Integer empresaId);
    TurnoDto actualizar(Long id, CreateTurnoDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}
