package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.producto_composicion.CreateProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.GuardarRecetaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaCosteoDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaResumenTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.UpdateProductoComposicionDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ProductoComposicionService {
    PageImpl<ProductoComposicionTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ProductoComposicionDto obtenerPorId(Long id, Integer empresaId);
    List<ProductoComposicionTableDto> listarPorPadre(Long productoPadreId);
    ProductoComposicionDto crear(CreateProductoComposicionDto dto, Integer empresaId);
    ProductoComposicionDto actualizar(Long id, UpdateProductoComposicionDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);

    // ── Receta completa ─────────────────────────────────────────────────────
    /** Una fila por producto con receta, en vez de una por ingrediente. */
    PageImpl<RecetaResumenTableDto> listarRecetas(PageableDto<Object> pageable, Integer empresaId);

    RecetaDto obtenerReceta(Long productoPadreId, Integer empresaId);

    /** Guarda la receta entera en una transacción: inserta, actualiza y borra. */
    RecetaDto guardarReceta(Long productoPadreId, GuardarRecetaDto dto, Integer empresaId);

    /** Copia la receta de otro producto. Pisa la del destino. */
    RecetaDto duplicarReceta(Long productoOrigenId, Long productoDestinoId, Integer empresaId);

    // ── Costeo ──────────────────────────────────────────────────────────────
    RecetaCosteoDto costear(Long productoPadreId, Integer empresaId);

    /** Costea y guarda el resultado en {@code producto.costo}. */
    RecetaCosteoDto aplicarCosto(Long productoPadreId, Integer empresaId);
}
