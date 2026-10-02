package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoTableDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.AdicionActivoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.CreateActivoFijoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.DepreciacionPeriodoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.DepreciarPeriodoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.MantenimientoActivoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ProyeccionDepreciacionDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ReporteActivosDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.RetiroActivoDto;
import com.cloud_technological.aura_pos.services.implementations.ActivoFijoServiceImpl;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/activos-fijos")
public class ActivoFijoController {

    @Autowired private ActivoFijoServiceImpl activoService;
    @Autowired private SecurityUtils securityUtils;

    private Integer usuario() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data));
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ActivoFijoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        return ok("Activos fijos", activoService.listar(pageable, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> getById(@PathVariable Long id) {
        return ok("Activo fijo", activoService.getById(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActivoFijoDto>> crear(@RequestBody CreateActivoFijoDto dto) {
        ActivoFijoDto result = activoService.crear(dto, securityUtils.getEmpresaId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED.value(), "Activo registrado", false, result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> actualizar(
            @PathVariable Long id, @RequestBody CreateActivoFijoDto dto) {
        return ok("Activo actualizado", activoService.actualizar(id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        activoService.eliminar(id, securityUtils.getEmpresaId());
        return ok("Activo eliminado", null);
    }

    // ── Depreciación ────────────────────────────────────────────────────────

    /** El cuerpo es opcional: solo lleva el uso del mes de los activos por unidades de producción. */
    @PostMapping("/depreciar/{periodoId}")
    public ResponseEntity<ApiResponse<List<DepreciacionPeriodoDto>>> calcularDepreciacion(
            @PathVariable Long periodoId,
            @RequestBody(required = false) DepreciarPeriodoDto dto) {
        List<DepreciacionPeriodoDto> resultado = activoService.calcularDepreciacionPeriodo(periodoId,
                dto != null ? dto.getUnidades() : null, securityUtils.getEmpresaId(), usuario());
        return ok("Depreciación calculada: " + resultado.size() + " activos procesados", resultado);
    }

    @PostMapping("/depreciar/{periodoId}/reversar")
    public ResponseEntity<ApiResponse<Integer>> reversarDepreciacion(@PathVariable Long periodoId) {
        int n = activoService.reversarDepreciacionPeriodo(periodoId, securityUtils.getEmpresaId(), usuario());
        return ok("Depreciación reversada: " + n + " activos", n);
    }

    @GetMapping("/{id}/historial-depreciacion")
    public ResponseEntity<ApiResponse<List<DepreciacionPeriodoDto>>> historialDepreciacion(@PathVariable Long id) {
        return ok("Historial", activoService.historialDepreciacion(id, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}/proyeccion")
    public ResponseEntity<ApiResponse<List<ProyeccionDepreciacionDto>>> proyeccion(@PathVariable Long id) {
        return ok("Proyección", activoService.proyeccion(id, securityUtils.getEmpresaId()));
    }

    // ── Adiciones y mantenimientos ──────────────────────────────────────────

    @GetMapping("/{id}/adiciones")
    public ResponseEntity<ApiResponse<List<AdicionActivoDto>>> adiciones(@PathVariable Long id) {
        return ok("Adiciones", activoService.adiciones(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/adiciones")
    public ResponseEntity<ApiResponse<AdicionActivoDto>> registrarAdicion(
            @PathVariable Long id, @RequestBody AdicionActivoDto dto) {
        return ok("Adición registrada", activoService.registrarAdicion(id, dto, securityUtils.getEmpresaId(), usuario()));
    }

    @GetMapping("/{id}/mantenimientos")
    public ResponseEntity<ApiResponse<List<MantenimientoActivoDto>>> mantenimientos(@PathVariable Long id) {
        return ok("Mantenimientos", activoService.mantenimientos(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/mantenimientos")
    public ResponseEntity<ApiResponse<MantenimientoActivoDto>> registrarMantenimiento(
            @PathVariable Long id, @RequestBody MantenimientoActivoDto dto) {
        return ok("Mantenimiento registrado", activoService.registrarMantenimiento(id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}/mantenimientos/{mantenimientoId}")
    public ResponseEntity<ApiResponse<Void>> eliminarMantenimiento(
            @PathVariable Long id, @PathVariable Long mantenimientoId) {
        activoService.eliminarMantenimiento(id, mantenimientoId, securityUtils.getEmpresaId());
        return ok("Mantenimiento eliminado", null);
    }

    // ── Baja y venta ────────────────────────────────────────────────────────

    @PutMapping("/{id}/dar-de-baja")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> darDeBaja(
            @PathVariable Long id, @RequestBody RetiroActivoDto dto) {
        return ok("Activo dado de baja", activoService.darDeBaja(id, dto, securityUtils.getEmpresaId(), usuario()));
    }

    @PutMapping("/{id}/vender")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> vender(
            @PathVariable Long id, @RequestBody RetiroActivoDto dto) {
        return ok("Venta del activo registrada", activoService.vender(id, dto, securityUtils.getEmpresaId(), usuario()));
    }

    @PutMapping("/{id}/anular-retiro")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> anularRetiro(@PathVariable Long id) {
        return ok("Retiro anulado", activoService.anularRetiro(id, securityUtils.getEmpresaId(), usuario()));
    }

    // ── Informe ─────────────────────────────────────────────────────────────

    @GetMapping("/reporte")
    public ResponseEntity<ApiResponse<ReporteActivosDto>> reporte(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Long centroCostoId,
            @RequestParam(required = false) Long periodoId,
            @RequestParam(required = false, defaultValue = "CATEGORIA") String agrupar) {
        return ok("Informe de activos", activoService.reporte(securityUtils.getEmpresaId(), estado, categoria,
                centroCostoId, periodoId, agrupar));
    }
}
