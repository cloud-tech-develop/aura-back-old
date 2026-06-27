package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.obligaciones.CreateObligacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.CuotaAmortizacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.ObligacionDto;
import com.cloud_technological.aura_pos.services.ObligacionFinancieraService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Obligaciones financieras (préstamos) con tabla de amortización. */
@RestController
@RequestMapping("/api/obligaciones-financieras")
public class ObligacionFinancieraController {

    @Autowired
    private ObligacionFinancieraService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ObligacionDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.listar(empresaId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ObligacionDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.obtenerPorId(id, empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ObligacionDto>> crear(@Valid @RequestBody CreateObligacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ObligacionDto created = service.crear(dto, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Obligación creada", false, created));
    }

    @PostMapping("/{id}/cuotas/{cuotaId}/pagar")
    public ResponseEntity<ApiResponse<CuotaAmortizacionDto>> pagarCuota(
            @PathVariable Long id, @PathVariable Long cuotaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CuotaAmortizacionDto cuota = service.pagarCuota(id, cuotaId, empresaId, usuarioId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Cuota pagada", false, cuota));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.anular(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Obligación anulada", false, null));
    }
}
