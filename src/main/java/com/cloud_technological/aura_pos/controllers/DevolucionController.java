package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.devolucion.CreateDevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionTableDto;
import com.cloud_technological.aura_pos.services.DevolucionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/devoluciones")
public class DevolucionController {

    @Autowired
    private DevolucionService devolucionService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<DevolucionTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<DevolucionTableDto> result = devolucionService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result),
                HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DevolucionDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        DevolucionDto result = devolucionService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Devolución encontrada", false, result),
                HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<DevolucionDto>> crear(
            @Valid @RequestBody CreateDevolucionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        DevolucionDto result = devolucionService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Devolución registrada exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Boolean>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        devolucionService.anular(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Devolución anulada correctamente", false, true),
                HttpStatus.OK);
    }
}
