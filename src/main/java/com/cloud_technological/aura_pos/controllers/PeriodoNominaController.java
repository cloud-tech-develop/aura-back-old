package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.periodo.CreatePeriodoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.periodo.PeriodoNominaDto;
import com.cloud_technological.aura_pos.services.PeriodoNominaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/periodos-nomina")
public class PeriodoNominaController {

    @Autowired
    private PeriodoNominaService periodoNominaService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PeriodoNominaDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<PeriodoNominaDto> result = periodoNominaService.listar(empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result),
                HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<PeriodoNominaDto>> crear(@RequestBody CreatePeriodoNominaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        PeriodoNominaDto result = periodoNominaService.crear(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Período creado exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<PeriodoNominaDto>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        PeriodoNominaDto result = periodoNominaService.anular(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Período anulado correctamente", false, result),
                HttpStatus.OK);
    }
}
