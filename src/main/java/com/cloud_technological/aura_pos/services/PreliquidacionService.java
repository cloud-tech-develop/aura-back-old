package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PreliquidacionItemDto;

public interface PreliquidacionService {
    List<PreliquidacionItemDto> previsualizar(Long periodoId, Integer empresaId);
}
