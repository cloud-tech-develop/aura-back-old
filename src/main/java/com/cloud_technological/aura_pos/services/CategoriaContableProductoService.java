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

    /**
     * Valida lo contable que se asigna a un producto: la categoría debe ser de
     * la empresa y estar activa, y cada override una auxiliar activa de su
     * clase. Todos pueden venir null (hereda). La cuenta de inventario es la
     * "cuenta de la compra": su clase depende de la clasificación del ítem
     * (14 mercancía, 15 activo fijo, 5 gasto…).
     */
    /** Crea una categoría nueva con las mismas cuentas y datos de otra. */
    CategoriaContableProductoDto copiar(Integer empresaId, Long origenId, String nombre);

    void validarCuentasProducto(Integer empresaId, Long categoriaContableId, Long cuentaIngresoId,
            Long cuentaCostoId, Long cuentaInventarioId,
            com.cloud_technological.aura_pos.utils.ClasificacionItem clasificacion);
}
