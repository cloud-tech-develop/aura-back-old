package com.cloud_technological.aura_pos.controllers;

import java.util.List;

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

import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.AgregarConceptosDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoAdjuntoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoTopeHorasDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CopiaPlantillaDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.GuardarCargoConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.GuardarCargoNovedadDto;
import com.cloud_technological.aura_pos.services.NominaCargoPlantillaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Plantilla de nómina del cargo (docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md, F5):
 * conceptos fijos, novedades permitidas, topes de horas extra y recargos,
 * adjuntos y copia desde otro cargo.
 */
@RestController
@RequestMapping("/api/nomina/cargos/{cargoId}")
public class NominaCargoPlantillaController {

    private final NominaCargoPlantillaService service;
    private final SecurityUtils securityUtils;

    public NominaCargoPlantillaController(NominaCargoPlantillaService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    // ── Conceptos ────────────────────────────────────────────────────────────

    @GetMapping("/conceptos")
    public ResponseEntity<ApiResponse<List<CargoConceptoDto>>> conceptos(@PathVariable Long cargoId) {
        return ok("Conceptos", service.conceptos(cargoId, securityUtils.getEmpresaId()));
    }

    /** Agrega varios conceptos por número (desde el buscador avanzado). */
    @PostMapping("/conceptos")
    public ResponseEntity<ApiResponse<List<CargoConceptoDto>>> agregarConceptos(@PathVariable Long cargoId,
            @RequestBody AgregarConceptosDto dto) {
        return ok("Conceptos agregados",
                service.agregarConceptos(cargoId, dto.getNumeros(), securityUtils.getEmpresaId()));
    }

    @PutMapping("/conceptos/{filaId}")
    public ResponseEntity<ApiResponse<List<CargoConceptoDto>>> actualizarConcepto(@PathVariable Long cargoId,
            @PathVariable Long filaId, @RequestBody GuardarCargoConceptoDto dto) {
        return ok("Concepto actualizado",
                service.actualizarConcepto(cargoId, filaId, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/conceptos/{filaId}")
    public ResponseEntity<ApiResponse<List<CargoConceptoDto>>> quitarConcepto(@PathVariable Long cargoId,
            @PathVariable Long filaId) {
        return ok("Concepto quitado", service.quitarConcepto(cargoId, filaId, securityUtils.getEmpresaId()));
    }

    // ── Novedades permitidas ─────────────────────────────────────────────────

    @GetMapping("/novedades")
    public ResponseEntity<ApiResponse<List<CargoNovedadDto>>> novedades(@PathVariable Long cargoId) {
        return ok("Novedades", service.novedades(cargoId, securityUtils.getEmpresaId()));
    }

    @PostMapping("/novedades")
    public ResponseEntity<ApiResponse<List<CargoNovedadDto>>> agregarNovedades(@PathVariable Long cargoId,
            @RequestBody AgregarConceptosDto dto) {
        return ok("Novedades agregadas",
                service.agregarNovedades(cargoId, dto.getNumeros(), securityUtils.getEmpresaId()));
    }

    @PutMapping("/novedades/{filaId}")
    public ResponseEntity<ApiResponse<List<CargoNovedadDto>>> actualizarNovedad(@PathVariable Long cargoId,
            @PathVariable Long filaId, @RequestBody GuardarCargoNovedadDto dto) {
        return ok("Novedad actualizada",
                service.actualizarNovedad(cargoId, filaId, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/novedades/{filaId}")
    public ResponseEntity<ApiResponse<List<CargoNovedadDto>>> quitarNovedad(@PathVariable Long cargoId,
            @PathVariable Long filaId) {
        return ok("Novedad quitada", service.quitarNovedad(cargoId, filaId, securityUtils.getEmpresaId()));
    }

    // ── Topes de horas ───────────────────────────────────────────────────────

    @GetMapping("/topes-horas")
    public ResponseEntity<ApiResponse<List<CargoTopeHorasDto>>> topes(@PathVariable Long cargoId) {
        return ok("Topes", service.topes(cargoId, securityUtils.getEmpresaId()));
    }

    /** Reemplaza todos los topes del cargo. */
    @PutMapping("/topes-horas")
    public ResponseEntity<ApiResponse<List<CargoTopeHorasDto>>> guardarTopes(@PathVariable Long cargoId,
            @RequestBody List<CargoTopeHorasDto> topes) {
        return ok("Topes guardados", service.guardarTopes(cargoId, topes, securityUtils.getEmpresaId()));
    }

    // ── Adjuntos ─────────────────────────────────────────────────────────────

    @GetMapping("/adjuntos")
    public ResponseEntity<ApiResponse<List<CargoAdjuntoDto>>> adjuntos(@PathVariable Long cargoId) {
        return ok("Adjuntos", service.adjuntos(cargoId, securityUtils.getEmpresaId()));
    }

    @PostMapping(value = "/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CargoAdjuntoDto>> subirAdjunto(@PathVariable Long cargoId,
            @RequestParam("archivo") MultipartFile archivo) {
        Long usuarioId = securityUtils.getUsuarioId();
        CargoAdjuntoDto r = service.subirAdjunto(cargoId, archivo, securityUtils.getEmpresaId(),
                usuarioId != null ? usuarioId.intValue() : null);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Adjunto cargado", false, r),
                HttpStatus.CREATED);
    }

    @DeleteMapping("/adjuntos/{adjuntoId}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarAdjunto(@PathVariable Long cargoId,
            @PathVariable Long adjuntoId) {
        service.eliminarAdjunto(cargoId, adjuntoId, securityUtils.getEmpresaId());
        return ok("Adjunto eliminado", true);
    }

    // ── Copiar ───────────────────────────────────────────────────────────────

    /** Copia conceptos, novedades y topes de otro cargo (lo que ya estaba no se toca). */
    @PostMapping("/copiar-de/{origenId}")
    public ResponseEntity<ApiResponse<CopiaPlantillaDto>> copiarDe(@PathVariable Long cargoId,
            @PathVariable Long origenId) {
        return ok("Plantilla copiada", service.copiarDe(cargoId, origenId, securityUtils.getEmpresaId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
