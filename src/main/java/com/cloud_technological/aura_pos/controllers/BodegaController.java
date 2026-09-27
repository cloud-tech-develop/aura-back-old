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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.bodegas.BodegaDto;
import com.cloud_technological.aura_pos.dto.bodegas.BodegaTableDto;
import com.cloud_technological.aura_pos.dto.bodegas.CreateBodegaDto;
import com.cloud_technological.aura_pos.dto.bodegas.UpdateBodegaDto;
import com.cloud_technological.aura_pos.services.BodegaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/bodegas")
public class BodegaController {

    @Autowired private BodegaService service;
    @Autowired private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<BodegaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<BodegaTableDto> result = service.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }

    /**
     * Combo de bodegas. Sin {@code sucursalId} usa la del token; con
     * {@code todas=true} trae las de la empresa, que es lo que necesita un
     * traslado entre sedes.
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<BodegaDto>>> list(
            @RequestParam(required = false) Integer sucursalId,
            @RequestParam(required = false, defaultValue = "false") boolean todas,
            @RequestParam(required = false, defaultValue = "false") boolean soloVenta) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer sucursal = todas ? null : resolverSucursal(sucursalId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "", false,
                service.list(empresaId, sucursal, soloVenta)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BodegaTableDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Bodega encontrada", false,
                service.obtenerPorId(id, empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BodegaTableDto>> crear(@Valid @RequestBody CreateBodegaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        BodegaTableDto result = service.crear(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Bodega creada exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BodegaTableDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBodegaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        BodegaTableDto result = service.actualizar(id, dto, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Bodega actualizada", false, result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Bodega eliminada", false, true));
    }

    private Integer resolverSucursal(Integer sucursalId) {
        if (sucursalId != null) return sucursalId;
        Long delToken = securityUtils.getSucursalId();
        return delToken != null ? delToken.intValue() : null;
    }
}
