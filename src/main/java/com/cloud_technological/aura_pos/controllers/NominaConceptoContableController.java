package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.contabilidad.ConceptoContableNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.contabilidad.GuardarConceptoContableDto;
import com.cloud_technological.aura_pos.services.NominaConceptoContableService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Cuenta y tercero por concepto de nómina ({@code recursos-humanos.cuentas-nomina}). */
@RestController
@RequestMapping("/api/nomina/conceptos-contables")
public class NominaConceptoContableController {

    private final NominaConceptoContableService service;
    private final SecurityUtils securityUtils;

    public NominaConceptoContableController(NominaConceptoContableService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConceptoContableNominaDto>>> listar() {
        return ok("Listado", service.listar(securityUtils.getEmpresaId()));
    }

    @PutMapping("/{codigo}")
    public ResponseEntity<ApiResponse<ConceptoContableNominaDto>> guardar(@PathVariable String codigo,
            @Valid @RequestBody GuardarConceptoContableDto dto) {
        return ok("Guardado", service.guardar(codigo, dto, securityUtils.getEmpresaId()));
    }

    /** Vuelve el concepto a su contabilización por defecto. */
    @DeleteMapping("/{codigo}")
    public ResponseEntity<ApiResponse<ConceptoContableNominaDto>> restablecer(@PathVariable String codigo) {
        return ok("Restablecido", service.restablecer(codigo, securityUtils.getEmpresaId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
