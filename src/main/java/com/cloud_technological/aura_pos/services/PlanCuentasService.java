package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.CreatePlanCuentaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.PlanCuentaDto;

public interface PlanCuentasService {
    List<PlanCuentaDto> listar(Integer empresaId);
    PlanCuentaDto crear(Integer empresaId, CreatePlanCuentaDto dto);
    PlanCuentaDto actualizar(Long id, Integer empresaId, CreatePlanCuentaDto dto);
    void eliminar(Long id, Integer empresaId);
    void seedPUC(Integer empresaId);
}
