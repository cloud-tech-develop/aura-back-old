package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.nomina.config.NominaConfigDto;
import com.cloud_technological.aura_pos.dto.nomina.config.UpdateNominaConfigDto;

public interface NominaConfigService {
    NominaConfigDto obtener(Integer empresaId);
    NominaConfigDto guardar(UpdateNominaConfigDto dto, Integer empresaId);
}
