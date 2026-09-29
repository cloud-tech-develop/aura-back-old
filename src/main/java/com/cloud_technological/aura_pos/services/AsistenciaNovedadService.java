package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia.AsistenciaNovedadDto;

public interface AsistenciaNovedadService {
    List<AsistenciaNovedadDto> generarDesdePeriodo(Long periodoNominaId, Integer empresaId, Long usuarioId);
    List<AsistenciaNovedadDto> listar(Long periodoNominaId, Integer empresaId);
    AsistenciaNovedadDto aprobar(Long id, Integer empresaId);
    AsistenciaNovedadDto rechazar(Long id, Integer empresaId);
}
