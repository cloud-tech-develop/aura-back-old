package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.reconteo.CreateReconteoDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoResponseDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoTableDto;
import com.cloud_technological.aura_pos.dto.reconteo.UpdateReconteoDetalleDto;
import com.cloud_technological.aura_pos.services.ReconteoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/reconteos")
public class ReconteoController {

    @Autowired
    private ReconteoService reconteoService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ReconteoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ReconteoTableDto> result = reconteoService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result),
                HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReconteoResponseDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ReconteoResponseDto result = reconteoService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Reconteo encontrado", false, result),
                HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ReconteoResponseDto>> crear(
            @RequestBody CreateReconteoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ReconteoResponseDto result = reconteoService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Reconteo creado exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/{reconteoId}/detalle/{detalleId}")
    public ResponseEntity<ApiResponse<ReconteoResponseDto>> actualizarDetalle(
            @PathVariable Long reconteoId,
            @PathVariable Long detalleId,
            @RequestBody UpdateReconteoDetalleDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ReconteoResponseDto result = reconteoService.actualizarDetalle(reconteoId, detalleId, dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Detalle actualizado", false, result),
                HttpStatus.OK);
    }

    @PutMapping("/{id}/aprobar")
    public ResponseEntity<ApiResponse<ReconteoResponseDto>> aprobar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ReconteoResponseDto result = reconteoService.aprobar(id, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Reconteo aprobado. Ajustes aplicados al inventario.", false, result),
                HttpStatus.OK);
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<ReconteoResponseDto>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ReconteoResponseDto result = reconteoService.anular(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Reconteo anulado correctamente", false, result),
                HttpStatus.OK);
    }
}
