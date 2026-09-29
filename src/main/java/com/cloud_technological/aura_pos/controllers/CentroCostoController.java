package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

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
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoDto;
import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoTableDto;
import com.cloud_technological.aura_pos.dto.centros_costos.CreateCentroCostoDto;
import com.cloud_technological.aura_pos.dto.centros_costos.UpdateCentroCostoDto;
import com.cloud_technological.aura_pos.services.CentroCostoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/centros-costos")
public class CentroCostoController {

    @Autowired private CentroCostoService service;
    @Autowired private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<CentroCostoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<CentroCostoTableDto> result = service.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CentroCostoTableDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        CentroCostoTableDto result = service.obtenerPorId(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Centro de costo encontrado", false, result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CentroCostoTableDto>> crear(
            @Valid @RequestBody CreateCentroCostoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CentroCostoTableDto result = service.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Centro de costo creado exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CentroCostoTableDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCentroCostoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CentroCostoTableDto result = service.actualizar(id, dto, empresaId, usuarioId);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Centro de costo actualizado correctamente", false, result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminar(id, empresaId);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Centro de costo eliminado correctamente", false, true));
    }

    /** Dropdown: lista plana de centros activos para selects */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<CentroCostoDto>>> list() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<CentroCostoDto> result = service.list(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "", false, result));
    }
}
