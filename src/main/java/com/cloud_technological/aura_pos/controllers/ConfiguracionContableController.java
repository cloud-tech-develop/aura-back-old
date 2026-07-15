package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaConfigDto;
import com.cloud_technological.aura_pos.dto.contabilidad.UpdateCuentaConfigDto;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Configuración del mapeo concepto contable → cuenta del PUC por empresa.
 * Permite a la empresa personalizar qué cuenta usa el motor de asientos.
 */
@RestController
@RequestMapping("/api/contabilidad/configuracion-cuentas")
public class ConfiguracionContableController {

    @Autowired
    private ConfiguracionContableService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CuentaConfigDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.listar(empresaId)));
    }

    @PutMapping("/{concepto}")
    public ResponseEntity<ApiResponse<CuentaConfigDto>> actualizar(
            @PathVariable String concepto,
            @Valid @RequestBody UpdateCuentaConfigDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ConceptoContable conceptoEnum = parseConcepto(concepto);
        CuentaConfigDto updated = service.actualizar(empresaId, conceptoEnum, dto.getCuentaId(),
                securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(200, "Configuración actualizada", false, updated));
    }

    /** Modo de contabilización de la empresa (E3): AUTOMATICO | REVISION. */
    @GetMapping("/modo")
    public ResponseEntity<ApiResponse<String>> obtenerModo() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.obtenerModo(empresaId)));
    }

    @PutMapping("/modo")
    public ResponseEntity<ApiResponse<String>> actualizarModo(
            @RequestBody java.util.Map<String, String> body) {
        Integer empresaId = securityUtils.getEmpresaId();
        String modo = service.actualizarModo(empresaId, body.get("modo"));
        return ResponseEntity.ok(new ApiResponse<>(200, "Modo actualizado", false, modo));
    }

    /** Historial de cambios del mapeo (auditoría), más reciente primero. */
    @GetMapping("/log")
    public ResponseEntity<ApiResponse<List<com.cloud_technological.aura_pos.dto.contabilidad.ConfigLogDto>>> listarLog() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.listarLog(empresaId)));
    }

    private ConceptoContable parseConcepto(String concepto) {
        try {
            return ConceptoContable.valueOf(concepto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Concepto contable inválido: " + concepto);
        }
    }
}
