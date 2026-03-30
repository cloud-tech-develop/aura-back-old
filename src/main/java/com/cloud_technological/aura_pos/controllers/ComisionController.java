package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.comision.ComisionConfigDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionConfigTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionLiquidacionDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionLiquidacionTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionVentaDto;
import com.cloud_technological.aura_pos.dto.comision.CreateComisionConfigDto;
import com.cloud_technological.aura_pos.dto.comision.CreateLiquidacionDto;
import com.cloud_technological.aura_pos.dto.comision.MarcarPagadaDto;
import com.cloud_technological.aura_pos.dto.comision.TecnicoDto;
import com.cloud_technological.aura_pos.services.ComisionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/comisiones")
public class ComisionController {

    @Autowired
    private ComisionService comisionService;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Técnicos activos de la empresa ────────────────────────

    @GetMapping("/tecnicos")
    public ResponseEntity<ApiResponse<List<TecnicoDto>>> listarTecnicos() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<TecnicoDto> result = comisionService.listarTecnicos(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }

    // ── Configuración ─────────────────────────────────────────

    @PostMapping("/config/page")
    public ResponseEntity<ApiResponse<PageImpl<ComisionConfigTableDto>>> listarConfig(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ComisionConfigTableDto> result = comisionService.listarConfig(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }

    @GetMapping("/config/{id}")
    public ResponseEntity<ApiResponse<ComisionConfigDto>> obtenerConfig(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ComisionConfigDto result = comisionService.obtenerConfigPorId(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Encontrado", false, result));
    }

    @PostMapping("/config/create")
    public ResponseEntity<ApiResponse<ComisionConfigDto>> crearConfig(
            @Valid @RequestBody CreateComisionConfigDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ComisionConfigDto result = comisionService.crearConfig(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Configuración creada", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/config/{id}")
    public ResponseEntity<ApiResponse<ComisionConfigDto>> actualizarConfig(
            @PathVariable Long id,
            @Valid @RequestBody CreateComisionConfigDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ComisionConfigDto result = comisionService.actualizarConfig(id, dto, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Configuración actualizada", false, result));
    }

    @PatchMapping("/config/{id}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggleConfig(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        comisionService.toggleConfig(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Estado actualizado", false, null));
    }

    // ── Liquidaciones ─────────────────────────────────────────

    @PostMapping("/liquidaciones/page")
    public ResponseEntity<ApiResponse<PageImpl<ComisionLiquidacionTableDto>>> listarLiquidaciones(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ComisionLiquidacionTableDto> result = comisionService.listarLiquidaciones(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }

    @GetMapping("/liquidaciones/{id}")
    public ResponseEntity<ApiResponse<ComisionLiquidacionDto>> obtenerLiquidacion(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ComisionLiquidacionDto result = comisionService.obtenerLiquidacionPorId(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Encontrado", false, result));
    }

    @PostMapping("/liquidaciones/create")
    public ResponseEntity<ApiResponse<ComisionLiquidacionDto>> crearLiquidacion(
            @Valid @RequestBody CreateLiquidacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ComisionLiquidacionDto result = comisionService.crearLiquidacion(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Liquidación creada", false, result),
                HttpStatus.CREATED);
    }

    @PatchMapping("/liquidaciones/{id}/pagar")
    public ResponseEntity<ApiResponse<Void>> marcarPagada(
            @PathVariable Long id,
            @Valid @RequestBody MarcarPagadaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        comisionService.marcarPagada(id, dto, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Liquidación marcada como pagada", false, null));
    }

    // ── Comisiones pendientes de un técnico ───────────────────

    @GetMapping("/pendientes")
    public ResponseEntity<ApiResponse<List<ComisionVentaDto>>> listarPendientes(
            @RequestParam Integer tecnicoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ComisionVentaDto> result = comisionService.listarPendientesTecnico(tecnicoId, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }
}
