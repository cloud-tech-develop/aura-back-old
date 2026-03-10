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

import com.cloud_technological.aura_pos.dto.producto_presentacion.CreateProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionTableDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.UpdateProductoPresentacionDto;
import com.cloud_technological.aura_pos.services.ProductoPresentacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/productos/presentaciones")
public class ProductoPresentacionController {

    @Autowired
    private ProductoPresentacionService presentacionService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ProductoPresentacionTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ProductoPresentacionTableDto> result = presentacionService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoPresentacionDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoPresentacionDto result = presentacionService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Presentación encontrada", false, result), HttpStatus.OK);
    }

    @GetMapping("/producto/{productoId}")
    public ResponseEntity<ApiResponse<List<ProductoPresentacionTableDto>>> listarPorProducto(
            @PathVariable Long productoId) {
        List<ProductoPresentacionTableDto> result = presentacionService.listarPorProducto(productoId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ProductoPresentacionDto>> crear(
            @Valid @RequestBody CreateProductoPresentacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoPresentacionDto result = presentacionService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Presentación creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoPresentacionDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductoPresentacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoPresentacionDto result = presentacionService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Presentación actualizada correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        presentacionService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Presentación eliminada correctamente", false, true), HttpStatus.OK);
    }
}