package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.productos.ConsumoComponenteDto;
import com.cloud_technological.aura_pos.dto.productos.CreateProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoInventarioDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoListDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoPosDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoTableDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateCodigoBarrasDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateProductoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ProductoService {
    PageImpl<ProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ProductoDto obtenerPorId(Long id, Integer empresaId);
    ProductoDto crear(CreateProductoDto dto, Integer empresaId);
    ProductoDto actualizar(Long id, UpdateProductoDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
    List<ProductoListDto> list(Integer empresaId);
    /** Lista simple filtrable por texto y por uso (VENTA/INSUMO/AMBOS). */
    List<ProductoListDto> list(Integer empresaId, String search, List<String> usos);
    List<ProductoPosDto> listarPos(Integer empresaId, Long sucursalId);
    ProductoDto actualizarCodigoBarras(Long id, UpdateCodigoBarrasDto dto, Integer empresaId);

    /**
     * Devuelve el código de barras del producto; si no tiene, genera uno EAN-13
     * interno y lo guarda. Es idempotente: reimprimir no cambia el código ni
     * pisa el que venga del proveedor.
     */
    ProductoDto generarCodigoBarras(Long id, Integer empresaId);

    /** Buscador para merma y obsequio: incluye insumos y productos ocultos del POS. */
    List<ProductoInventarioDto> buscarInventario(Integer empresaId, Long sucursalId, String search);
    /** Coincidencia exacta por SKU o código de barras (escáner). */
    ProductoInventarioDto buscarPorCodigo(Integer empresaId, Long sucursalId, String codigo);

    ProductoInventarioDto buscarInventarioPorId(Integer empresaId, Long sucursalId, Long productoId);

    /** Con bodega: el stock de esa sola bodega (traslados entre bodegas de la misma sede). */
    ProductoInventarioDto buscarInventarioPorId(Integer empresaId, Long sucursalId, Long bodegaId, Long productoId);
    /** Componentes que saldrían del inventario por {@code cantidad} unidades de un producto con receta. */
    List<ConsumoComponenteDto> explosion(Long productoId, BigDecimal cantidad, Long sucursalId, Integer empresaId);
}
