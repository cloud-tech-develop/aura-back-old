package com.cloud_technological.aura_pos.controllers;

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

import com.cloud_technological.aura_pos.dto.sucursal.CreateSucursalDto;
import com.cloud_technological.aura_pos.dto.sucursal.SucursalDto;
import com.cloud_technological.aura_pos.dto.sucursal.SucursalTableDto;
import com.cloud_technological.aura_pos.dto.sucursal.UpdateSucursalDto;
import com.cloud_technological.aura_pos.services.SucursalService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/sucursales")
public class SucursalController {

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<SucursalTableDto>>> paginar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<SucursalTableDto> result = sucursalService.paginar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/activas")
    public ResponseEntity<ApiResponse<java.util.List<SucursalDto>>> listarActivas() {
        Integer empresaId = securityUtils.getEmpresaId();
        java.util.List<SucursalDto> result = sucursalService.listarActivas(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Sucursales activas", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SucursalDto>> obtenerPorId(@PathVariable Integer id) {
        Integer empresaId = securityUtils.getEmpresaId();
        SucursalDto result = sucursalService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Sucursal encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<SucursalDto>> crear(@Valid @RequestBody CreateSucursalDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        SucursalDto result = sucursalService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Sucursal creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SucursalDto>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateSucursalDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        SucursalDto result = sucursalService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Sucursal actualizada correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Integer id) {
        Integer empresaId = securityUtils.getEmpresaId();
        sucursalService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Sucursal desactivada correctamente", false, true), HttpStatus.OK);
    }
}