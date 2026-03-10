package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import com.cloud_technological.aura_pos.services.DashboardService;
import com.cloud_technological.aura_pos.dto.dashboard.DashboardDto;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;


@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardDto>> obtener() {
        Integer empresaId = securityUtils.getEmpresaId();
        DashboardDto result = dashboardService.obtener(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Dashboard cargado", false, result), HttpStatus.OK);
    }

    @GetMapping("/ventas-semana")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> ventasPorDiaSemana() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<Map<String, Object>> result = dashboardService.ventasPorDiaSemana(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @GetMapping("/ventas-metodo-pago")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> ventasPorMetodoPago() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<Map<String, Object>> result = dashboardService.ventasPorMetodoPago(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }
}
