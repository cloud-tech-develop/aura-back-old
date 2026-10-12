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

import com.cloud_technological.aura_pos.dto.nomina.catalogos.CreateNominaCatalogoDto;
import com.cloud_technological.aura_pos.dto.nomina.catalogos.NominaCatalogoTableDto;
import com.cloud_technological.aura_pos.dto.nomina.catalogos.TipoCatalogoNominaDto;
import com.cloud_technological.aura_pos.services.NominaCatalogoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Catálogos simples de nómina: {@code /api/nomina/catalogos/{catalogo}} donde
 * {@code catalogo} es grupos-nomina, niveles-cargo, tipos-cargo, tipos-embargo,
 * motivos-retiro, motivos-ausentismo o riesgos-arl. Cada uno tiene su permiso
 * (recursos-humanos.&lt;catalogo&gt;) en PermisoRutas.
 */
@RestController
@RequestMapping("/api/nomina/catalogos")
public class NominaCatalogoController {

    private final NominaCatalogoService service;
    private final SecurityUtils securityUtils;

    public NominaCatalogoController(NominaCatalogoService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    /** Definición de todos los catálogos (título y campos propios). */
    @GetMapping("/tipos")
    public ResponseEntity<ApiResponse<List<TipoCatalogoNominaDto>>> tipos() {
        return ok("Catálogos de nómina", service.tipos());
    }

    /** Definición de un catálogo. */
    @GetMapping("/{catalogo}/tipo")
    public ResponseEntity<ApiResponse<TipoCatalogoNominaDto>> tipo(@PathVariable String catalogo) {
        return ok("Catálogo", service.tipo(catalogo));
    }

    @PostMapping("/{catalogo}/page")
    public ResponseEntity<ApiResponse<PageImpl<NominaCatalogoTableDto>>> listar(@PathVariable String catalogo,
            @RequestBody PageableDto<Object> pageable) {
        return ok("Listado", service.listar(catalogo, pageable, securityUtils.getEmpresaId()));
    }

    /** Activos para selects de otras pantallas. */
    @GetMapping("/{catalogo}/list")
    public ResponseEntity<ApiResponse<List<NominaCatalogoTableDto>>> list(@PathVariable String catalogo) {
        return ok("Listado", service.activos(catalogo, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{catalogo}/{id}")
    public ResponseEntity<ApiResponse<NominaCatalogoTableDto>> obtener(@PathVariable String catalogo,
            @PathVariable Long id) {
        return ok("Elemento", service.obtener(catalogo, id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{catalogo}")
    public ResponseEntity<ApiResponse<NominaCatalogoTableDto>> crear(@PathVariable String catalogo,
            @Valid @RequestBody CreateNominaCatalogoDto dto) {
        NominaCatalogoTableDto r = service.crear(catalogo, dto, securityUtils.getEmpresaId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Creado", false, r),
                HttpStatus.CREATED);
    }

    @PutMapping("/{catalogo}/{id}")
    public ResponseEntity<ApiResponse<NominaCatalogoTableDto>> actualizar(@PathVariable String catalogo,
            @PathVariable Long id, @Valid @RequestBody CreateNominaCatalogoDto dto) {
        return ok("Actualizado", service.actualizar(catalogo, id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{catalogo}/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable String catalogo, @PathVariable Long id) {
        service.eliminar(catalogo, id, securityUtils.getEmpresaId());
        return ok("Eliminado", true);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
