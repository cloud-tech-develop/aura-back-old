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

import com.cloud_technological.aura_pos.dto.nomina.embargo.EmbargoDtos.CreateEmbargoDto;
import com.cloud_technological.aura_pos.dto.nomina.embargo.EmbargoDtos.EmbargoDto;
import com.cloud_technological.aura_pos.dto.nomina.embargo.EmbargoDtos.TerminarEmbargoDto;
import com.cloud_technological.aura_pos.services.implementations.EmbargoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Embargos sobre el salario (V113).
 *
 * <p>El monto es "o valor total o porcentaje", nunca ambos. Alimentos tiene
 * prelación y entra siempre en prioridad 1.
 */
@RestController
@RequestMapping("/api/embargo")
public class EmbargoController {

    @Autowired
    private EmbargoService embargoService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/contrato/{contratoId}")
    public ResponseEntity<ApiResponse<List<EmbargoDto>>> porContrato(@PathVariable Long contratoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Embargos consultados", embargoService.listar(contratoId, empresaId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmbargoDto>> crear(@RequestBody CreateEmbargoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        EmbargoDto data = embargoService.crear(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Embargo registrado", false, data),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}/terminar")
    public ResponseEntity<ApiResponse<EmbargoDto>> terminar(
            @PathVariable Long id,
            @RequestBody(required = false) TerminarEmbargoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        EmbargoDto data = embargoService.terminar(id, empresaId,
                dto != null ? dto.getFechaFin() : null);
        return ok("Embargo terminado", data);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
