package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.CreateVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.FiltroVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CentroCostoVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateNovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateVinculacionConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.NovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.VinculacionConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionTableDto;
import com.cloud_technological.aura_pos.services.VinculacionDetalleService;
import com.cloud_technological.aura_pos.services.VinculacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Vinculaciones ({@code recursos-humanos.vinculaciones}). El cambio de salario y la
 * terminación siguen en {@code /api/contrato}.
 */
@RestController
@RequestMapping("/api/nomina/vinculaciones")
public class VinculacionController {

    private final VinculacionService service;
    private final VinculacionDetalleService detalle;
    private final SecurityUtils securityUtils;

    public VinculacionController(VinculacionService service, VinculacionDetalleService detalle,
            SecurityUtils securityUtils) {
        this.service = service;
        this.detalle = detalle;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<VinculacionTableDto>>> listar(
            @RequestBody PageableDto<FiltroVinculacionDto> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VinculacionDto>> obtener(@PathVariable Long id) {
        return ok("Vinculación", service.obtener(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VinculacionDto>> crear(@Valid @RequestBody CreateVinculacionDto dto) {
        VinculacionDto r = service.crear(dto, securityUtils.getEmpresaId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Vinculación creada", false, r),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VinculacionDto>> actualizar(@PathVariable Long id,
            @Valid @RequestBody CreateVinculacionDto dto) {
        return ok("Vinculación actualizada", service.actualizar(id, dto, securityUtils.getEmpresaId()));
    }

    /** Sube el soporte: {@code tipo} = interno (acto/documento interno) o externo (contrato firmado). */
    @PostMapping(value = "/{id}/soporte/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VinculacionDto>> subirSoporte(@PathVariable Long id, @PathVariable String tipo,
            @RequestParam("archivo") MultipartFile archivo) {
        return ok("Soporte cargado", service.subirSoporte(id, tipo, archivo, securityUtils.getEmpresaId()));
    }

    // ── Centros de costo ─────────────────────────────────────────────────────

    @GetMapping("/{id}/centros-costo")
    public ResponseEntity<ApiResponse<List<CentroCostoVinculacionDto>>> centrosCosto(@PathVariable Long id) {
        return ok("Centros de costo", detalle.centrosCosto(id, securityUtils.getEmpresaId()));
    }

    /** Reemplaza la distribución (vacía = sin distribución; si no, suma 100 %). */
    @PutMapping("/{id}/centros-costo")
    public ResponseEntity<ApiResponse<List<CentroCostoVinculacionDto>>> guardarCentrosCosto(@PathVariable Long id,
            @Valid @RequestBody List<CentroCostoVinculacionDto> centros) {
        return ok("Centros de costo guardados", detalle.guardarCentrosCosto(id, centros, securityUtils.getEmpresaId()));
    }

    // ── Conceptos propios ────────────────────────────────────────────────────

    @GetMapping("/{id}/conceptos")
    public ResponseEntity<ApiResponse<List<VinculacionConceptoDto>>> conceptos(@PathVariable Long id) {
        return ok("Conceptos", detalle.conceptos(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/conceptos")
    public ResponseEntity<ApiResponse<List<VinculacionConceptoDto>>> crearConcepto(@PathVariable Long id,
            @Valid @RequestBody CreateVinculacionConceptoDto dto) {
        return ok("Concepto agregado", detalle.crearConcepto(id, dto, securityUtils.getEmpresaId()));
    }

    @PutMapping("/{id}/conceptos/{conceptoId}")
    public ResponseEntity<ApiResponse<List<VinculacionConceptoDto>>> actualizarConcepto(@PathVariable Long id,
            @PathVariable Long conceptoId, @Valid @RequestBody CreateVinculacionConceptoDto dto) {
        return ok("Concepto actualizado", detalle.actualizarConcepto(id, conceptoId, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}/conceptos/{conceptoId}")
    public ResponseEntity<ApiResponse<List<VinculacionConceptoDto>>> eliminarConcepto(@PathVariable Long id,
            @PathVariable Long conceptoId) {
        return ok("Concepto eliminado", detalle.eliminarConcepto(id, conceptoId, securityUtils.getEmpresaId()));
    }

    // ── Novedades permitidas ─────────────────────────────────────────────────

    @GetMapping("/{id}/novedades-permitidas")
    public ResponseEntity<ApiResponse<List<NovedadPermitidaDto>>> novedades(@PathVariable Long id) {
        return ok("Novedades permitidas", detalle.novedadesPermitidas(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/novedades-permitidas")
    public ResponseEntity<ApiResponse<List<NovedadPermitidaDto>>> crearNovedad(@PathVariable Long id,
            @Valid @RequestBody CreateNovedadPermitidaDto dto) {
        return ok("Novedad permitida", detalle.crearNovedad(id, dto, securityUtils.getEmpresaId()));
    }

    @PutMapping("/{id}/novedades-permitidas/{novedadId}")
    public ResponseEntity<ApiResponse<List<NovedadPermitidaDto>>> actualizarNovedad(@PathVariable Long id,
            @PathVariable Long novedadId, @Valid @RequestBody CreateNovedadPermitidaDto dto) {
        return ok("Novedad actualizada", detalle.actualizarNovedad(id, novedadId, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}/novedades-permitidas/{novedadId}")
    public ResponseEntity<ApiResponse<List<NovedadPermitidaDto>>> eliminarNovedad(@PathVariable Long id,
            @PathVariable Long novedadId) {
        return ok("Novedad quitada", detalle.eliminarNovedad(id, novedadId, securityUtils.getEmpresaId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
