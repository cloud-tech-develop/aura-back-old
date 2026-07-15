package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.CategoriaContableProductoDto;

/**
 * Categorías contables de producto (E4): parametrizan a qué cuentas van
 * ingreso/inventario/costo/devolución por grupo de productos.
 */
public interface CategoriaContableProductoService {

    List<CategoriaContableProductoDto> listar(Integer empresaId);

    CategoriaContableProductoDto crear(Integer empresaId, CategoriaContableProductoDto dto);

    CategoriaContableProductoDto actualizar(Integer empresaId, Long id,
            CategoriaContableProductoDto dto);

    /**
     * Siembra la categoría "General" (cuentas actuales 4135/1435/6135) si no
     * existe. Los productos sin categoría contabilizan idéntico a hoy.
     */
    void seedDefaults(Integer empresaId);
}
