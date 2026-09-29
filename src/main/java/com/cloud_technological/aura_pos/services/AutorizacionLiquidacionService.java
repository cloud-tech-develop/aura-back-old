package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia.AutorizacionDto;
import com.cloud_technological.aura_pos.dto.asistencia.CrearAutorizacionDto;

public interface AutorizacionLiquidacionService {
    AutorizacionDto crear(CrearAutorizacionDto dto, Integer empresaId, Long usuarioId);
    List<AutorizacionDto> listarPorPeriodo(Long periodoNominaId, Integer empresaId);
    void anular(Long id, Integer empresaId);
}
