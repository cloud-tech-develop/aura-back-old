package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.periodo.CreatePeriodoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.periodo.PeriodoNominaDto;

public interface PeriodoNominaService {
    List<PeriodoNominaDto> listar(Integer empresaId);
    PeriodoNominaDto crear(CreatePeriodoNominaDto dto, Integer empresaId);
    PeriodoNominaDto anular(Long id, Integer empresaId);
}
