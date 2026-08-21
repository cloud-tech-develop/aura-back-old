package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.traslado_fondos.CreateTrasladoFondosDto;
import com.cloud_technological.aura_pos.dto.traslado_fondos.TrasladoFondosDto;
import com.cloud_technological.aura_pos.services.TrasladoFondosService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Traslados de dinero entre bolsillos de la empresa: constitución y reembolso
 * de la caja menor, consignación del efectivo del día y movimientos entre
 * cuentas bancarias.
 */
@RestController
@RequestMapping("/api/traslados-fondos")
public class TrasladoFondosController {

    @Autowired
    private TrasladoFondosService trasladoFondosService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<ApiResponse<TrasladoFondosDto>> crear(
            @Valid @RequestBody CreateTrasladoFondosDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        TrasladoFondosDto creado = trasladoFondosService.crear(empresaId,
                usuarioId != null ? usuarioId.intValue() : null, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ApiResponse<>(201, "Traslado de fondos registrado", false, creado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TrasladoFondosDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                trasladoFondosService.obtener(id, empresaId)));
    }

    /** Sin fechas lista el mes en curso; el concepto filtra por tipo de traslado. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TrasladoFondosDto>>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String concepto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                trasladoFondosService.listar(empresaId, desde, hasta, concepto)));
    }
}
