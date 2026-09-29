package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.asistencia.AutorizacionDto;
import com.cloud_technological.aura_pos.dto.asistencia.CrearAutorizacionDto;
import com.cloud_technological.aura_pos.services.AutorizacionLiquidacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/nomina/autorizaciones")
public class AutorizacionLiquidacionController {

    @Autowired
    private AutorizacionLiquidacionService autorizacionService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<ApiResponse<AutorizacionDto>> crear(@RequestBody CrearAutorizacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Autorización excepcional registrada", false,
                        autorizacionService.crear(dto, empresaId, usuarioId)), HttpStatus.OK);
    }

    @GetMapping("/periodo/{periodoNominaId}")
    public ResponseEntity<ApiResponse<List<AutorizacionDto>>> listar(@PathVariable Long periodoNominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Autorizaciones obtenidas", false,
                        autorizacionService.listarPorPeriodo(periodoNominaId, empresaId)), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        autorizacionService.anular(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Autorización anulada", false, null), HttpStatus.OK);
    }
}
