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
import com.cloud_technological.aura_pos.dto.activos_fijos.CreateActivoFijoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.DepreciacionPeriodoDto;
import com.cloud_technological.aura_pos.services.implementations.ActivoFijoServiceImpl;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/activos-fijos")
public class ActivoFijoController {

    @Autowired private ActivoFijoServiceImpl activoService;
    @Autowired private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ActivoFijoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Activos fijos",
                false, activoService.listar(pageable, empresaId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> getById(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Activo fijo",
                false, activoService.getById(id, empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActivoFijoDto>> crear(@RequestBody CreateActivoFijoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ActivoFijoDto result = activoService.crear(dto, empresaId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED.value(), "Activo registrado", false, result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> actualizar(
            @PathVariable Long id, @RequestBody CreateActivoFijoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Activo actualizado",
                false, activoService.actualizar(id, dto, empresaId)));
    }

    @PutMapping("/{id}/dar-de-baja")
    public ResponseEntity<ApiResponse<ActivoFijoDto>> darDeBaja(
            @PathVariable Long id,
            @RequestParam(required = false) String observaciones) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Activo dado de baja",
                false, activoService.darDeBaja(id, observaciones, empresaId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        activoService.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Activo eliminado", false, null));
    }

    @PostMapping("/depreciar/{periodoId}")
    public ResponseEntity<ApiResponse<List<DepreciacionPeriodoDto>>> calcularDepreciacion(
            @PathVariable Long periodoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        List<DepreciacionPeriodoDto> resultado = activoService.calcularDepreciacionPeriodo(
                periodoId, empresaId, usuarioId.intValue());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(),
                "Depreciación calculada: " + resultado.size() + " activos procesados", false, resultado));
    }

    @GetMapping("/{id}/historial-depreciacion")
    public ResponseEntity<ApiResponse<List<DepreciacionPeriodoDto>>> historialDepreciacion(
            @PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Historial",
                false, activoService.historialDepreciacion(id, empresaId)));
    }
}
