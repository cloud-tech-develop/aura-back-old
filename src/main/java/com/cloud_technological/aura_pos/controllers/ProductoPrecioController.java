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

import com.cloud_technological.aura_pos.dto.lista_precios.CreateProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioTableDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateProductoPrecioDto;
import com.cloud_technological.aura_pos.services.ProductoPrecioService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/productos/precios")
public class ProductoPrecioController {

    @Autowired
    private ProductoPrecioService productoPrecioService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ProductoPrecioTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ProductoPrecioTableDto> result = productoPrecioService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoPrecioDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoPrecioDto result = productoPrecioService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/lista/{listaPrecioId}")
    public ResponseEntity<ApiResponse<List<ProductoPrecioTableDto>>> listarPorLista(
            @PathVariable Long listaPrecioId) {
        List<ProductoPrecioTableDto> result = productoPrecioService.listarPorLista(listaPrecioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ProductoPrecioDto>> crear(@Valid @RequestBody CreateProductoPrecioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoPrecioDto result = productoPrecioService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Precio creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoPrecioDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductoPrecioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoPrecioDto result = productoPrecioService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio actualizado correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        productoPrecioService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio eliminado correctamente", false, true), HttpStatus.OK);
    }
}