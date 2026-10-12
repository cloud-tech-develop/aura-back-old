package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.CreateTipoComprobanteDto;
import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.services.NominaTipoComprobanteService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Tipos de comprobante de nómina ({@code recursos-humanos.tipos-comprobante-nomina}). */
@RestController
@RequestMapping("/api/nomina/tipos-comprobante")
public class NominaTipoComprobanteController {

    private final NominaTipoComprobanteService service;
    private final SecurityUtils securityUtils;

    public NominaTipoComprobanteController(NominaTipoComprobanteService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<TipoComprobanteTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    /** Activos para selects. */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<TipoComprobanteTableDto>>> list() {
        return ok("Listado", service.activos(securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TipoComprobanteTableDto>> obtener(@PathVariable Long id) {
        return ok("Tipo de comprobante", service.obtener(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TipoComprobanteTableDto>> crear(@Valid @RequestBody CreateTipoComprobanteDto dto) {
        TipoComprobanteTableDto r = service.crear(dto, securityUtils.getEmpresaId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Creado", false, r),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TipoComprobanteTableDto>> actualizar(@PathVariable Long id,
            @Valid @RequestBody CreateTipoComprobanteDto dto) {
        return ok("Actualizado", service.actualizar(id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        service.eliminar(id, securityUtils.getEmpresaId());
        return ok("Eliminado", true);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
