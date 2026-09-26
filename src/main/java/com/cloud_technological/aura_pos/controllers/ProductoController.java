package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.productos.CambioUnidadPreviewDto;
import com.cloud_technological.aura_pos.dto.productos.CambioUnidadRequestDto;
import com.cloud_technological.aura_pos.dto.productos.ConsumoComponenteDto;
import com.cloud_technological.aura_pos.dto.productos.CreateProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoInventarioDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoListDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoPosDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoTableDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateCodigoBarrasDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateProductoDto;
import com.cloud_technological.aura_pos.services.CambioUnidadProductoService;
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

    @Autowired
    private CambioUnidadProductoService cambioUnidadProductoService;


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

    /**
     * Lista simple. {@code search} filtra por nombre, SKU o código de barras;
     * {@code uso} (p. ej. {@code INSUMO,AMBOS}) la restringe por uso del producto.
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ProductoListDto>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> uso) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false,
                productoService.list(empresaId, search, uso)), HttpStatus.OK);
    }

    @GetMapping("/pos")
    public ResponseEntity<ApiResponse<List<ProductoPosDto>>> listarPos() {
        Integer empresaId = securityUtils.getEmpresaId();
        Long sucursalId = securityUtils.getSucursalId();
        List<ProductoPosDto> result = productoService.listarPos(empresaId, sucursalId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    /** Buscador para merma y obsequio: a diferencia de /pos, incluye insumos. */
    @GetMapping("/inventario")
    public ResponseEntity<ApiResponse<List<ProductoInventarioDto>>> buscarInventario(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long sucursalId) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ProductoInventarioDto> result = productoService.buscarInventario(
                empresaId, resolverSucursal(sucursalId), search);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/inventario/id/{productoId}")
    public ResponseEntity<ApiResponse<ProductoInventarioDto>> buscarInventarioPorId(
            @PathVariable Long productoId,
            @RequestParam(required = false) Long sucursalId) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoInventarioDto result = productoService.buscarInventarioPorId(
                empresaId, resolverSucursal(sucursalId), productoId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Producto encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/inventario/codigo/{codigo}")
    public ResponseEntity<ApiResponse<ProductoInventarioDto>> buscarPorCodigo(
            @PathVariable String codigo,
            @RequestParam(required = false) Long sucursalId) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoInventarioDto result = productoService.buscarPorCodigo(
                empresaId, resolverSucursal(sucursalId), codigo);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Producto encontrado", false, result), HttpStatus.OK);
    }

    /** Componentes que saldrían del inventario por una cantidad de un producto con receta. */
    @GetMapping("/{id}/explosion")
    public ResponseEntity<ApiResponse<List<ConsumoComponenteDto>>> explosion(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal cantidad,
            @RequestParam(required = false) Long sucursalId) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ConsumoComponenteDto> result = productoService.explosion(
                id, cantidad, resolverSucursal(sucursalId), empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "OK", false, result), HttpStatus.OK);
    }

    /** Vista previa de "Pasar a unidad": qué cambia si la presentación pequeña pasa a ser la base. */
    @GetMapping("/{id}/cambio-unidad")
    public ResponseEntity<ApiResponse<CambioUnidadPreviewDto>> previewCambioUnidad(
            @PathVariable Long id,
            @RequestParam Long presentacionId) {
        Integer empresaId = securityUtils.getEmpresaId();
        CambioUnidadPreviewDto result = cambioUnidadProductoService.preview(id, presentacionId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "OK", false, result), HttpStatus.OK);
    }

    @PostMapping("/{id}/cambio-unidad")
    public ResponseEntity<ApiResponse<CambioUnidadPreviewDto>> aplicarCambioUnidad(
            @PathVariable Long id,
            @Valid @RequestBody CambioUnidadRequestDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CambioUnidadPreviewDto result = cambioUnidadProductoService.aplicar(id, dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Producto pasado a unidad", false, result), HttpStatus.OK);
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

    /** Reimprimir no debe cambiar el código: si ya tiene, devuelve el mismo. */
    @PostMapping("/{id}/codigo-barras/generar")
    public ResponseEntity<ApiResponse<ProductoDto>> generarCodigoBarras(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProductoDto result = productoService.generarCodigoBarras(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Código de barras listo", false, result),
                HttpStatus.OK);
    }

    /** El form de merma/obsequio manda la sucursal elegida; si no, la del token. */
    private Long resolverSucursal(Long sucursalId) {
        return sucursalId != null ? sucursalId : securityUtils.getSucursalId();
    }
}
