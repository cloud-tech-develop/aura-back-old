package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.productos.CreateProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoListDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoPosDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoTableDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateCodigoBarrasDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateProductoDto;
import com.cloud_technological.aura_pos.services.ProductoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private SecurityUtils securityUtils;


    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ProductoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ProductoTableDto> result = productoService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    /**
     * Obtiene un producto por su id.
     * @param id id del producto a obtener.
     * @return ResponseEntity con el objeto producto y un estado HTTP OK.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoDto result = productoService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Producto encontrado", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ProductoDto>> crear(@Valid @RequestBody CreateProductoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoDto result = productoService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Producto creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoDto result = productoService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Producto actualizado correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        productoService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Producto eliminado correctamente", false, true), HttpStatus.OK);
    }
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ProductoListDto>>> list() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, productoService.list(empresaId)), HttpStatus.OK);
    }

    @GetMapping("/pos")
    public ResponseEntity<ApiResponse<List<ProductoPosDto>>> listarPos() {
        Integer empresaId = securityUtils.getEmpresaId();
        Long sucursalId = securityUtils.getSucursalId();
        List<ProductoPosDto> result = productoService.listarPos(empresaId, sucursalId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }
    @PatchMapping("/{id}/codigo-barras")
    public ResponseEntity<ApiResponse<ProductoDto>> actualizarCodigoBarras(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCodigoBarrasDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoDto result = productoService.actualizarCodigoBarras(id, dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Código de barras actualizado", false, result),
                HttpStatus.OK);
    }
}