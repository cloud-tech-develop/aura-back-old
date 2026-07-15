package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.contabilidad.ImpuestoDto;
import com.cloud_technological.aura_pos.services.ImpuestoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** CRUD de impuestos parametrizables (E5). */
@RestController
@RequestMapping("/api/contabilidad/impuestos")
public class ImpuestoController {

    @Autowired
    private ImpuestoService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ImpuestoDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.listar(empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ImpuestoDto>> crear(@Valid @RequestBody ImpuestoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ImpuestoDto created = service.crear(empresaId, dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Impuesto creado", false, created),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ImpuestoDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ImpuestoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ImpuestoDto updated = service.actualizar(empresaId, id, dto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Impuesto actualizado", false, updated));
    }

    /** Siembra los impuestos estándar (idempotente). */
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<Void>> seed() {
        Integer empresaId = securityUtils.getEmpresaId();
        service.seedDefaults(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Impuestos sembrados", false, null));
    }
}
