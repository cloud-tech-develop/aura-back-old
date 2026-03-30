package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.cierre_contable.CierreContableDto;
import com.cloud_technological.aura_pos.dto.cierre_contable.ReporteIvaDto;
import com.cloud_technological.aura_pos.repositories.cierre_contable.CierreContableQueryRepository;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/cierre-contable")
public class CierreContableController {

    @Autowired
    private CierreContableQueryRepository repository;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<CierreContableDto>> obtener(
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta) {

        Integer empresaId = securityUtils.getEmpresaId();

        // Default: mes actual
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String desde = (fechaDesde != null && !fechaDesde.isBlank())
                ? fechaDesde : hoy.withDayOfMonth(1).format(fmt);
        String hasta = (fechaHasta != null && !fechaHasta.isBlank())
                ? fechaHasta : hoy.format(fmt);

        CierreContableDto resultado = repository.construir(empresaId, desde, hasta);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Cierre contable", false, resultado));
    }

    @GetMapping("/reporte-iva")
    public ResponseEntity<ApiResponse<ReporteIvaDto>> reporteIva(
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta) {

        Integer empresaId = securityUtils.getEmpresaId();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String desde = (fechaDesde != null && !fechaDesde.isBlank())
                ? fechaDesde : hoy.withDayOfMonth(1).format(fmt);
        String hasta = (fechaHasta != null && !fechaHasta.isBlank())
                ? fechaHasta : hoy.format(fmt);

        ReporteIvaDto resultado = repository.reporteIva(empresaId, desde, hasta);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Reporte IVA", false, resultado));
    }
}
