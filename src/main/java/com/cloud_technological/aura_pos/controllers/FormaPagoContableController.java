package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.contabilidad.FormaPagoContableDto;
import com.cloud_technological.aura_pos.services.FormaPagoContableService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Formas de pago con cuenta contable asociada (E2 · pieza 2). El contador
 * mapea NEQUI→111005 una vez y todos los asientos de pago la usan.
 */
@RestController
@RequestMapping("/api/contabilidad/formas-pago")
public class FormaPagoContableController {

    @Autowired
    private FormaPagoContableService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FormaPagoContableDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.listar(empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FormaPagoContableDto>> crear(
            @Valid @RequestBody FormaPagoContableDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        FormaPagoContableDto created = service.crear(empresaId, dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Forma de pago creada", false, created),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FormaPagoContableDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody FormaPagoContableDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        FormaPagoContableDto updated = service.actualizar(empresaId, id, dto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Forma de pago actualizada", false, updated));
    }

    /** Siembra las formas estándar (idempotente). */
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<Void>> seed() {
        Integer empresaId = securityUtils.getEmpresaId();
        service.seedDefaults(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Formas de pago sembradas", false, null));
    }
}
