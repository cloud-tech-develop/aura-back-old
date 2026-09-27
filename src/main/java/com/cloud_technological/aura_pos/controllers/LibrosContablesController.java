package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroAuxiliarDto;
import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroDiarioDto;
import com.cloud_technological.aura_pos.services.LibrosContablesService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Libros oficiales: auxiliar por tercero y libro diario. */
@RestController
@RequestMapping("/api/contabilidad/libros")
public class LibrosContablesController {

    @Autowired
    private LibrosContablesService librosService;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Cuentas por rango de prefijo PUC ("13" a "13" = toda la cartera) y,
     * opcionalmente, un solo tercero.
     */
    @GetMapping("/auxiliar-tercero")
    public ResponseEntity<ApiResponse<LibroAuxiliarDto>> auxiliarTercero(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String cuentaDesde,
            @RequestParam(required = false) String cuentaHasta,
            @RequestParam(required = false) Long terceroId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Auxiliar por tercero", false,
                librosService.auxiliarPorTercero(empresaId, desde, hasta,
                        cuentaDesde, cuentaHasta, terceroId)));
    }

    @GetMapping("/diario")
    public ResponseEntity<ApiResponse<LibroDiarioDto>> diario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Libro diario", false,
                librosService.diario(empresaId, desde, hasta)));
    }
}
