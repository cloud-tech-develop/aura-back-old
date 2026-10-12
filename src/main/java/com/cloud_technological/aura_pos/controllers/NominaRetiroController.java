package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.FiltroRetiroDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.RegistrarRetiroDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.RetiroDto;
import com.cloud_technological.aura_pos.services.NominaRetiroService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Retiros de vinculaciones ({@code recursos-humanos.retiros}). */
@RestController
@RequestMapping("/api/nomina/retiros")
public class NominaRetiroController {

    private final NominaRetiroService service;
    private final SecurityUtils securityUtils;

    public NominaRetiroController(NominaRetiroService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<RetiroDto>>> listar(@RequestBody PageableDto<FiltroRetiroDto> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RetiroDto>> obtener(@PathVariable Long id) {
        return ok("Retiro", service.obtener(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RetiroDto>> registrar(@Valid @RequestBody RegistrarRetiroDto dto) {
        RetiroDto r = service.registrar(dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Retiro registrado", false, r),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<RetiroDto>> anular(@PathVariable Long id) {
        return ok("Retiro anulado", service.anular(id, securityUtils.getEmpresaId()));
    }

    /** Genera la prenómina de liquidación definitiva del retiro; devuelve el documento. */
    @PostMapping("/{id}/liquidacion")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> generarLiquidacion(@PathVariable Long id) {
        DocumentoNominaDto doc = service.generarLiquidacion(id, securityUtils.getEmpresaId(),
                securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Liquidación generada", false, doc),
                HttpStatus.CREATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
