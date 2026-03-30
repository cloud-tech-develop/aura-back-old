package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import com.cloud_technological.aura_pos.dto.dashboard.DashboardDto;
import com.cloud_technological.aura_pos.repositories.dashboard.DashboardQueryRepository;
import com.cloud_technological.aura_pos.services.DashboardService;

@Service
public class DashboardServiceImpl implements DashboardService{
    private final DashboardQueryRepository dashboardRepository;

    @Autowired
    public DashboardServiceImpl(DashboardQueryRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Override
    public DashboardDto obtener(Integer empresaId) {
        DashboardDto dashboard = new DashboardDto();
        dashboard.setVentasHoy(dashboardRepository.resumenVentasHoy(empresaId));
        dashboard.setVentasMes(dashboardRepository.resumenVentasMes(empresaId));
        dashboard.setTotalComprasMes(dashboardRepository.totalComprasMes(empresaId));
        dashboard.setStockBajo(dashboardRepository.stockBajo(empresaId));
        dashboard.setLotesProximosVencer(dashboardRepository.lotesProximosVencer(empresaId));
        dashboard.setUltimasVentas(dashboardRepository.ultimasVentas(empresaId));
        dashboard.setTopProductos(dashboardRepository.topProductosMes(empresaId));
        dashboard.setUltimosMovimientos(dashboardRepository.ultimosMovimientos(empresaId));
        dashboard.setTotalInventarioCosto(dashboardRepository.totalInventarioCosto(empresaId));
        return dashboard;
    }

    @Override
    public List<Map<String, Object>> ventasPorDiaSemana(Integer empresaId) {
        return dashboardRepository.ventasPorDiaSemana(empresaId);
    }

    @Override
    public List<Map<String, Object>> ventasPorMetodoPago(Integer empresaId) {
        return dashboardRepository.ventasPorMetodoPago(empresaId);
    }
}
