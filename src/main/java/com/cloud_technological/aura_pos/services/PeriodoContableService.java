package com.cloud_technological.aura_pos.services;

import java.util.List;
import java.util.Optional;

import com.cloud_technological.aura_pos.dto.periodo_contable.AbrirPeriodoDto;
import com.cloud_technological.aura_pos.dto.periodo_contable.CerrarPeriodoDto;
import com.cloud_technological.aura_pos.dto.periodo_contable.PeriodoContableTableDto;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;

public interface PeriodoContableService {

    List<PeriodoContableTableDto> listar(Integer empresaId);

    PeriodoContableTableDto abrirPeriodo(AbrirPeriodoDto dto, Integer empresaId, Long usuarioId);

    PeriodoContableTableDto cerrarPeriodo(Long id, CerrarPeriodoDto dto, Integer empresaId, Long usuarioId);

    Optional<PeriodoContableEntity> getPeriodoAbierto(Integer empresaId);
}
