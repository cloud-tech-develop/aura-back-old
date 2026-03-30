package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.tesoreria.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CuentaBancariaDto;
import com.cloud_technological.aura_pos.services.CuentaBancariaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/tesoreria/cuentas")
public class CuentaBancariaController {

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CuentaBancariaDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<CuentaBancariaDto> data = cuentaBancariaService.listar(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CuentaBancariaDto>> crear(
            @Valid @RequestBody CreateCuentaBancariaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaBancariaDto saved = cuentaBancariaService.crear(empresaId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Cuenta creada", false, saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CuentaBancariaDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CreateCuentaBancariaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaBancariaDto updated = cuentaBancariaService.actualizar(id, empresaId, dto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Cuenta actualizada", false, updated));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggle(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        cuentaBancariaService.toggleActiva(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Estado actualizado", false, null));
    }
}
