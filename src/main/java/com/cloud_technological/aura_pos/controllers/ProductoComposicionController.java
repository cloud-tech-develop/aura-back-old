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

import com.cloud_technological.aura_pos.dto.producto_composicion.CreateProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.GuardarRecetaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaCosteoDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaResumenTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.UpdateProductoComposicionDto;
import com.cloud_technological.aura_pos.services.ProductoComposicionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/productos/composicion")
public class ProductoComposicionController {

    @Autowired
    private ProductoComposicionService composicionService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ProductoComposicionTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ProductoComposicionTableDto> result = composicionService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoComposicionDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoComposicionDto result = composicionService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Composición encontrada", false, result), HttpStatus.OK);
    }

    @GetMapping("/padre/{productoPadreId}")
    public ResponseEntity<ApiResponse<List<ProductoComposicionTableDto>>> listarPorPadre(
            @PathVariable Long productoPadreId) {
        List<ProductoComposicionTableDto> result = composicionService.listarPorPadre(productoPadreId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ProductoComposicionDto>> crear(
            @Valid @RequestBody CreateProductoComposicionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoComposicionDto result = composicionService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Composición creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductoComposicionDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductoComposicionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoComposicionDto result = composicionService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Composición actualizada correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        composicionService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Composición eliminada correctamente", false, true), HttpStatus.OK);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Receta completa — evita el alta línea por línea
    // ════════════════════════════════════════════════════════════════════════

    /** Una fila por producto con receta (no por ingrediente). */
    @PostMapping("/recetas/page")
    public ResponseEntity<ApiResponse<PageImpl<RecetaResumenTableDto>>> listarRecetas(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<RecetaResumenTableDto> result = composicionService.listarRecetas(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    /** Receta completa lista para pintar la grilla de edición. */
    @GetMapping("/receta/{productoPadreId}")
    public ResponseEntity<ApiResponse<RecetaDto>> obtenerReceta(@PathVariable Long productoPadreId) {
        Integer empresaId = securityUtils.getEmpresaId();
        RecetaDto result = composicionService.obtenerReceta(productoPadreId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Receta encontrada", false, result), HttpStatus.OK);
    }

    /**
     * Guarda la receta entera de un producto en una sola transacción.
     *
     * Es un reemplazo total: los componentes que no vengan en el body se borran.
     */
    @PutMapping("/receta/{productoPadreId}")
    public ResponseEntity<ApiResponse<RecetaDto>> guardarReceta(
            @PathVariable Long productoPadreId,
            @Valid @RequestBody GuardarRecetaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        RecetaDto result = composicionService.guardarReceta(productoPadreId, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Receta guardada correctamente", false, result), HttpStatus.OK);
    }

    /** Copia la receta de otro producto. Pisa la del destino. */
    @PostMapping("/receta/{productoDestinoId}/duplicar-de/{productoOrigenId}")
    public ResponseEntity<ApiResponse<RecetaDto>> duplicarReceta(
            @PathVariable Long productoDestinoId,
            @PathVariable Long productoOrigenId) {
        Integer empresaId = securityUtils.getEmpresaId();
        RecetaDto result = composicionService.duplicarReceta(productoOrigenId, productoDestinoId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Receta duplicada correctamente", false, result), HttpStatus.OK);
    }

    /** "¿Cuánto me cuesta producir uno?" — no modifica nada. */
    @GetMapping("/receta/{productoPadreId}/costeo")
    public ResponseEntity<ApiResponse<RecetaCosteoDto>> costear(@PathVariable Long productoPadreId) {
        Integer empresaId = securityUtils.getEmpresaId();
        RecetaCosteoDto result = composicionService.costear(productoPadreId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Costeo calculado", false, result), HttpStatus.OK);
    }

    /** Costea y guarda el resultado en el costo del producto. */
    @PostMapping("/receta/{productoPadreId}/aplicar-costo")
    public ResponseEntity<ApiResponse<RecetaCosteoDto>> aplicarCosto(@PathVariable Long productoPadreId) {
        Integer empresaId = securityUtils.getEmpresaId();
        RecetaCosteoDto result = composicionService.aplicarCosto(productoPadreId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Costo aplicado al producto", false, result), HttpStatus.OK);
    }
}