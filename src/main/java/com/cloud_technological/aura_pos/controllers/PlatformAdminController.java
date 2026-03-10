package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.error_log.ErrorLogDetalleDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogGrupoDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogPageParamsDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogTableDto;
import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.DashboardPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaTableDto;
import com.cloud_technological.aura_pos.dto.super_admin.UpdateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.services.EmpresaPlataformaService;
import com.cloud_technological.aura_pos.services.ErrorLogService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    @Autowired
    private EmpresaPlataformaService empresaService;

    @Autowired
    private ErrorLogService errorLogService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardPlataformaDto>> dashboard() {
        DashboardPlataformaDto result = empresaService.dashboard();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Dashboard obtenido", false, result), HttpStatus.OK);
    }

    @PostMapping("/empresas/page")
    public ResponseEntity<ApiResponse<PageImpl<EmpresaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        PageImpl<EmpresaTableDto> result = empresaService.listar(pageable);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/empresas/{id}")
    public ResponseEntity<ApiResponse<EmpresaPlataformaDto>> obtenerPorId(@PathVariable Integer id) {
        EmpresaPlataformaDto result = empresaService.obtenerPorId(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping("/empresas")
    public ResponseEntity<ApiResponse<EmpresaPlataformaDto>> crear(
            @Valid @RequestBody CreateEmpresaPlataformaDto dto) {
        EmpresaPlataformaDto result = empresaService.crear(dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Empresa creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/empresas/{id}")
    public ResponseEntity<ApiResponse<EmpresaPlataformaDto>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateEmpresaPlataformaDto dto) {
        EmpresaPlataformaDto result = empresaService.actualizar(id, dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa actualizada exitosamente", false, result), HttpStatus.OK);
    }

    @PatchMapping("/empresas/{id}/suspender")
    public ResponseEntity<ApiResponse<Boolean>> suspender(@PathVariable Integer id) {
        empresaService.suspender(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa suspendida correctamente", false, true), HttpStatus.OK);
    }

    @PatchMapping("/empresas/{id}/activar")
    public ResponseEntity<ApiResponse<Boolean>> activar(@PathVariable Integer id) {
        empresaService.activar(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa activada correctamente", false, true), HttpStatus.OK);
    }

    // ─── Error Logs ───────────────────────────────────────────

    @PostMapping("/error-logs/page")
    public ResponseEntity<ApiResponse<PageImpl<ErrorLogTableDto>>> errorLogs(
            @RequestBody PageableDto<ErrorLogPageParamsDto> pageable) {
        var result = errorLogService.listar(pageable);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @PostMapping("/error-logs/grupos")
    public ResponseEntity<ApiResponse<PageImpl<ErrorLogGrupoDto>>> errorLogGrupos(
            @RequestBody PageableDto<ErrorLogPageParamsDto> pageable) {
        var result = errorLogService.listarGrupos(pageable);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Grupos obtenidos", false, result), HttpStatus.OK);
    }

    @GetMapping("/error-logs/{id}")
    public ResponseEntity<ApiResponse<ErrorLogDetalleDto>> errorLogDetalle(@PathVariable Long id) {
        ErrorLogDetalleDto result = errorLogService.obtenerPorId(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Detalle obtenido", false, result), HttpStatus.OK);
    }
}