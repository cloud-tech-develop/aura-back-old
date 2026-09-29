package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.GuardarBorradorDto;
import com.cloud_technological.aura_pos.services.AsistenciaFrenteService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/asistencia/frentes")
public class AsistenciaFrenteController {

    @Autowired private AsistenciaFrenteService service;
    @Autowired private SecurityUtils securityUtils;

    @GetMapping("/{frenteId}/fecha/{fecha}")
    public ResponseEntity<ApiResponse<AsistenciaFrenteDto>> obtener(
            @PathVariable Long frenteId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.obtener(frenteId, fecha, empresaId)));
    }

    @PostMapping("/{frenteId}/borrador")
    public ResponseEntity<ApiResponse<AsistenciaFrenteDto>> guardarBorrador(
            @PathVariable Long frenteId, @Valid @RequestBody GuardarBorradorDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Borrador guardado", false,
                service.guardarBorrador(frenteId, dto, empresaId, usuarioId)));
    }

    @PostMapping("/{frenteId}/soporte-pdf")
    public ResponseEntity<ApiResponse<AsistenciaFrenteDto>> subirSoporte(
            @PathVariable Long frenteId,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam("file") MultipartFile file) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Soporte PDF cargado", false,
                service.subirSoporte(frenteId, fecha, file, empresaId, usuarioId)));
    }

    @PostMapping("/{asistenciaId}/enviar-revision")
    public ResponseEntity<ApiResponse<Boolean>> enviarRevision(@PathVariable Long asistenciaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        service.enviarRevision(asistenciaId, empresaId, usuarioId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(),
                "Asistencia enviada a revisión", false, true));
    }
}
