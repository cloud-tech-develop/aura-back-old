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

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.FiltroIncrementoDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDetalleDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoRequestDto;
import com.cloud_technological.aura_pos.services.NominaIncrementoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Incrementos salariales y su retroactivo ({@code recursos-humanos.incrementos}). */
@RestController
@RequestMapping("/api/nomina/incrementos")
public class NominaIncrementoController {

    private final NominaIncrementoService service;
    private final SecurityUtils securityUtils;

    public NominaIncrementoController(NominaIncrementoService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<IncrementoDto>>> listar(
            @RequestBody PageableDto<FiltroIncrementoDto> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncrementoDto>> obtener(@PathVariable Long id) {
        return ok("Incremento", service.obtener(id, securityUtils.getEmpresaId()));
    }

    /** Vista previa: salario actual → nuevo por vinculación. */
    @GetMapping("/{id}/detalle")
    public ResponseEntity<ApiResponse<List<IncrementoDetalleDto>>> detalle(@PathVariable Long id) {
        return ok("Detalle", service.detalle(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IncrementoDto>> crear(@Valid @RequestBody IncrementoRequestDto dto) {
        IncrementoDto r = service.crear(dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Incremento calculado", false, r),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IncrementoDto>> actualizar(@PathVariable Long id,
            @Valid @RequestBody IncrementoRequestDto dto) {
        return ok("Incremento recalculado", service.actualizar(id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}/detalle/{detalleId}")
    public ResponseEntity<ApiResponse<IncrementoDto>> quitar(@PathVariable Long id, @PathVariable Long detalleId) {
        return ok("Vinculación quitada", service.quitar(id, detalleId, securityUtils.getEmpresaId()));
    }

    @PutMapping("/{id}/aplicar")
    public ResponseEntity<ApiResponse<IncrementoDto>> aplicar(@PathVariable Long id) {
        return ok("Incremento aplicado",
                service.aplicar(id, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PutMapping("/{id}/reversar")
    public ResponseEntity<ApiResponse<IncrementoDto>> reversar(@PathVariable Long id) {
        return ok("Incremento reversado", service.reversar(id, securityUtils.getEmpresaId()));
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<IncrementoDto>> anular(@PathVariable Long id) {
        return ok("Incremento anulado", service.anular(id, securityUtils.getEmpresaId()));
    }

    /** Genera la prenómina del retroactivo; devuelve el documento. */
    @PostMapping("/{id}/retroactivo")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> retroactivo(@PathVariable Long id) {
        DocumentoNominaDto doc = service.generarRetroactivo(id, securityUtils.getEmpresaId(),
                securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Retroactivo generado", false, doc),
                HttpStatus.CREATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
