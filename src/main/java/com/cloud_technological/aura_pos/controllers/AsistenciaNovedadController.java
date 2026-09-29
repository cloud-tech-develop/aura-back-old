package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.asistencia.AsistenciaNovedadDto;
import com.cloud_technological.aura_pos.services.AsistenciaNovedadService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/asistencia/novedades")
public class AsistenciaNovedadController {

    @Autowired
    private AsistenciaNovedadService novedadService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/generar/periodo/{periodoNominaId}")
    public ResponseEntity<ApiResponse<List<AsistenciaNovedadDto>>> generar(@PathVariable Long periodoNominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Novedades generadas desde asistencia", false,
                        novedadService.generarDesdePeriodo(periodoNominaId, empresaId, usuarioId)), HttpStatus.OK);
    }

    @GetMapping("/periodo/{periodoNominaId}")
    public ResponseEntity<ApiResponse<List<AsistenciaNovedadDto>>> listar(@PathVariable Long periodoNominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Novedades obtenidas", false,
                        novedadService.listar(periodoNominaId, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/aprobar")
    public ResponseEntity<ApiResponse<AsistenciaNovedadDto>> aprobar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Novedad aprobada", false,
                        novedadService.aprobar(id, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<ApiResponse<AsistenciaNovedadDto>> rechazar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Novedad rechazada", false,
                        novedadService.rechazar(id, empresaId)), HttpStatus.OK);
    }
}
