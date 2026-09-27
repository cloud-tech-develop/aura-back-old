package com.cloud_technological.aura_pos.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.cartera.credito.CreateSolicitudCreditoDto;
import com.cloud_technological.aura_pos.dto.cartera.credito.ReglaCreditoDto;
import com.cloud_technological.aura_pos.dto.cartera.credito.SimulacionReglaDto;
import com.cloud_technological.aura_pos.dto.cartera.credito.SolicitudCreditoDto;
import com.cloud_technological.aura_pos.services.implementations.ReglaCreditoService;
import com.cloud_technological.aura_pos.services.implementations.SolicitudCreditoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Reglas automáticas de crédito y autorizaciones para pasar el cupo. */
@RestController
@RequestMapping("/api/cartera")
public class ControlCreditoController {

    @Autowired private ReglaCreditoService reglaService;
    @Autowired private SolicitudCreditoService solicitudService;
    @Autowired private SecurityUtils securityUtils;

    // ── Reglas ──────────────────────────────────────────────────────────
    @GetMapping("/reglas")
    public ResponseEntity<ApiResponse<List<ReglaCreditoDto>>> reglas() {
        return ok(reglaService.listar(securityUtils.getEmpresaId()));
    }

    @PostMapping("/reglas")
    public ResponseEntity<ApiResponse<ReglaCreditoDto>> crearRegla(@RequestBody ReglaCreditoDto dto) {
        return new ResponseEntity<>(new ApiResponse<>(201, "Regla creada", false,
                reglaService.guardar(null, dto, securityUtils.getEmpresaId())), HttpStatus.CREATED);
    }

    @PutMapping("/reglas/{id}")
    public ResponseEntity<ApiResponse<ReglaCreditoDto>> actualizarRegla(@PathVariable Long id, @RequestBody ReglaCreditoDto dto) {
        return ok(reglaService.guardar(id, dto, securityUtils.getEmpresaId()));
    }

    @PatchMapping("/reglas/{id}/activo")
    public ResponseEntity<ApiResponse<ReglaCreditoDto>> activarRegla(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ok(reglaService.cambiarActivo(id, Boolean.TRUE.equals(body.get("activo")), securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/reglas/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarRegla(@PathVariable Long id) {
        reglaService.eliminar(id, securityUtils.getEmpresaId());
        return ResponseEntity.ok(new ApiResponse<>(200, "Regla eliminada", false, null));
    }

    @PostMapping("/reglas/simular")
    public ResponseEntity<ApiResponse<List<SimulacionReglaDto>>> simular(@RequestBody ReglaCreditoDto dto) {
        return ok(reglaService.simular(dto, securityUtils.getEmpresaId()));
    }

    // ── Solicitudes de autorización ─────────────────────────────────────
    @GetMapping("/solicitudes")
    public ResponseEntity<ApiResponse<List<SolicitudCreditoDto>>> solicitudes(@RequestParam(required = false) String estado) {
        return ok(solicitudService.listar(securityUtils.getEmpresaId(), estado));
    }

    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<ApiResponse<SolicitudCreditoDto>> solicitud(@PathVariable Long id) {
        return ok(solicitudService.obtener(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/solicitudes")
    public ResponseEntity<ApiResponse<SolicitudCreditoDto>> solicitar(@RequestBody CreateSolicitudCreditoDto dto) {
        return new ResponseEntity<>(new ApiResponse<>(201, "Autorización solicitada", false,
                solicitudService.crear(dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId())), HttpStatus.CREATED);
    }

    @PostMapping("/solicitudes/{id}/aprobar")
    public ResponseEntity<ApiResponse<SolicitudCreditoDto>> aprobar(@PathVariable Long id,
            @RequestBody(required = false) Map<String, Integer> body) {
        Integer horas = body != null ? body.get("vigenciaHoras") : null;
        return ok(solicitudService.aprobar(id, horas, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<ApiResponse<SolicitudCreditoDto>> rechazar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ok(solicitudService.rechazar(id, body != null ? body.get("motivo") : null,
                securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, data));
    }
}
