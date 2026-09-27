package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto;
import com.cloud_technological.aura_pos.services.DeclaracionesService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Borradores de declaraciones leídos del mayor. */
@RestController
@RequestMapping("/api/contabilidad/declaraciones")
public class DeclaracionesController {

    @Autowired
    private DeclaracionesService service;

    @Autowired
    private SecurityUtils securityUtils;

    /** IVA (formulario 300): el período lo elige el usuario (bimestral o cuatrimestral). */
    @GetMapping("/iva")
    public ResponseEntity<ApiResponse<BorradorDeclaracionDto>> iva(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Borrador IVA", false,
                service.iva(securityUtils.getEmpresaId(), desde, hasta)));
    }

    /** Retención en la fuente (formulario 350): mensual. */
    @GetMapping("/retencion")
    public ResponseEntity<ApiResponse<BorradorDeclaracionDto>> retencion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Borrador retención", false,
                service.retencion(securityUtils.getEmpresaId(), desde, hasta)));
    }
}
