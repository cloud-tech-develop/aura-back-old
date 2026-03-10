package com.cloud_technological.aura_pos.services;

import java.util.List;
import java.util.Map;

import com.cloud_technological.aura_pos.dto.dashboard.DashboardDto;

public interface DashboardService {
    DashboardDto obtener(Integer empresaId);
    List<Map<String, Object>> ventasPorDiaSemana(Integer empresaId);
    List<Map<String, Object>> ventasPorMetodoPago(Integer empresaId);
}
