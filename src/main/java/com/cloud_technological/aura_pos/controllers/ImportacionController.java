package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.DocumentosAbiertos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.PlanCuentas;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Saldos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Terceros;
import com.cloud_technological.aura_pos.services.ImportacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Migración desde otro software: el Excel se lee en el navegador y llega aquí
 * como filas. /validar no graba; /confirmar graba todo o nada.
 */
@RestController
@RequestMapping("/api/importacion")
public class ImportacionController {

    @Autowired
    private ImportacionService service;

    @Autowired
    private SecurityUtils securityUtils;

    private ResponseEntity<ApiResponse<Resultado>> ok(Resultado r) {
        return ResponseEntity.ok(new ApiResponse<>(200, r.isConfirmado() ? "Importación realizada" : "Validación",
                false, r));
    }

    private Integer usuarioInt() {
        Long u = securityUtils.getUsuarioId();
        return u != null ? u.intValue() : null;
    }

    @PostMapping("/plan-cuentas/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarPlan(@RequestBody PlanCuentas req) {
        return ok(service.validarPlan(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/plan-cuentas/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarPlan(@RequestBody PlanCuentas req) {
        return ok(service.confirmarPlan(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/terceros/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarTerceros(@RequestBody Terceros req) {
        return ok(service.validarTerceros(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/terceros/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarTerceros(@RequestBody Terceros req) {
        return ok(service.confirmarTerceros(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/saldos/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarSaldos(@RequestBody Saldos req) {
        return ok(service.validarSaldos(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/saldos/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarSaldos(@RequestBody Saldos req) {
        return ok(service.confirmarSaldos(securityUtils.getEmpresaId(), usuarioInt(), req));
    }

    @PostMapping("/documentos/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarDocumentos(@RequestBody DocumentosAbiertos req) {
        return ok(service.validarDocumentos(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/documentos/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarDocumentos(@RequestBody DocumentosAbiertos req) {
        return ok(service.confirmarDocumentos(securityUtils.getEmpresaId(), securityUtils.getUsuarioId(), req));
    }
}
