package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

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

import com.cloud_technological.aura_pos.dto.periodo_contable.AbrirPeriodoDto;
import com.cloud_technological.aura_pos.dto.periodo_contable.CerrarPeriodoDto;
import com.cloud_technological.aura_pos.dto.periodo_contable.PeriodoContableTableDto;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.services.PeriodoContableService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/periodos-contables")
public class PeriodoContableController {

    @Autowired private PeriodoContableService service;
    @Autowired private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PeriodoContableTableDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<PeriodoContableTableDto> result = service.listar(empresaId);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Períodos contables", false, result));
    }

    @GetMapping("/activo")
    public ResponseEntity<ApiResponse<PeriodoContableEntity>> periodoActivo() {
        Integer empresaId = securityUtils.getEmpresaId();
        PeriodoContableEntity periodo = service.getPeriodoAbierto(empresaId).orElse(null);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Período activo", false, periodo));
    }

    @PostMapping("/abrir")
    public ResponseEntity<ApiResponse<PeriodoContableTableDto>> abrir(
            @Valid @RequestBody AbrirPeriodoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        PeriodoContableTableDto result = service.abrirPeriodo(dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Período abierto exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}/cerrar")
    public ResponseEntity<ApiResponse<PeriodoContableTableDto>> cerrar(
            @PathVariable Long id,
            @RequestBody(required = false) CerrarPeriodoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CerrarPeriodoDto body = dto != null ? dto : new CerrarPeriodoDto();
        PeriodoContableTableDto result = service.cerrarPeriodo(id, body, empresaId, usuarioId);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Período cerrado exitosamente", false, result));
    }
}
