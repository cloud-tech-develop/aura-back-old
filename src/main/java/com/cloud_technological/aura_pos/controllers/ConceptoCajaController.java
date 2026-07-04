package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.conceptos_caja.ConceptoCajaDto;
import com.cloud_technological.aura_pos.dto.conceptos_caja.CreateConceptoCajaDto;
import com.cloud_technological.aura_pos.services.ConceptoCajaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Catálogo de conceptos de caja (concepto amigable ↔ cuenta contable). */
@RestController
@RequestMapping("/api/conceptos-caja")
public class ConceptoCajaController {

    @Autowired private ConceptoCajaService service;
    @Autowired private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConceptoCajaDto>>> listar(
            @RequestParam(required = false) String tipo) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.listar(empresaId, tipo)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConceptoCajaDto>> crear(
            @Valid @RequestBody CreateConceptoCajaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ConceptoCajaDto created = service.crear(dto, empresaId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Concepto creado", false, created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ConceptoCajaDto>> actualizar(
            @PathVariable Long id, @Valid @RequestBody CreateConceptoCajaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Concepto actualizado", false,
                service.actualizar(id, dto, empresaId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Concepto eliminado", false, null));
    }
}
