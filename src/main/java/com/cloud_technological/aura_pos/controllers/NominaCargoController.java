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

import com.cloud_technological.aura_pos.dto.nomina.cargos.CreateNominaCargoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.NominaCargoTableDto;
import com.cloud_technological.aura_pos.services.NominaCargoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Cargos de nómina ({@code recursos-humanos.cargos}). */
@RestController
@RequestMapping("/api/nomina/cargos")
public class NominaCargoController {

    private final NominaCargoService service;
    private final SecurityUtils securityUtils;

    public NominaCargoController(NominaCargoService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<NominaCargoTableDto>>> listar(@RequestBody PageableDto<Object> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    /** Activos para selects. */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<NominaCargoTableDto>>> list() {
        return ok("Listado", service.activos(securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NominaCargoTableDto>> obtener(@PathVariable Long id) {
        return ok("Cargo", service.obtener(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NominaCargoTableDto>> crear(@Valid @RequestBody CreateNominaCargoDto dto) {
        NominaCargoTableDto r = service.crear(dto, securityUtils.getEmpresaId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Creado", false, r),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NominaCargoTableDto>> actualizar(@PathVariable Long id,
            @Valid @RequestBody CreateNominaCargoDto dto) {
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
