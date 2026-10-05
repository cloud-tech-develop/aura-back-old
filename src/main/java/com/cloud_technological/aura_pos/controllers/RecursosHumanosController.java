package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.DashboardRrhhDto;
import com.cloud_technological.aura_pos.services.nomina.DashboardRrhhService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/recursos-humanos")
public class RecursosHumanosController {

    @Autowired
    private DashboardRrhhService dashboardService;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Resumen del Centro de Recursos Humanos (/recursos-humanos en el front):
     * nómina del mes y del anterior, serie enero..mes, costo por rubro, personal
     * y pendientes. Sin parámetros usa el mes en curso.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardRrhhDto>> dashboard(
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate hoy = LocalDate.now();
        int a = anio != null ? anio : hoy.getYear();
        int m = mes != null ? mes : hoy.getMonthValue();
        if (m < 1 || m > 12) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(400, "El mes debe estar entre 1 y 12", true, null));
        }
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, dashboardService.resumen(empresaId, a, m)));
    }
}
