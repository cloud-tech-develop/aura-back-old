package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;

import com.cloud_technological.aura_pos.dto.productos.HistorialCostoProductoDto;
import com.cloud_technological.aura_pos.entity.ProductoEntity;

/** Historial del costo de cada producto (V226). */
public interface CostoHistorialService {

    /**
     * Guarda un movimiento de un documento que pasó por el costo promedio,
     * cambie o no el costo: una compra al mismo costo también es parte del
     * rastro.
     *
     * @param producto      producto ya con el costo nuevo
     * @param origen        documento que lo movió
     * @param cantidad      unidades base, con signo
     * @param valor         valor total del movimiento, con signo
     * @param stockDespues  existencia total ya con el movimiento
     * @param costoAnterior costo promedio antes del movimiento
     */
    void registrarMovimiento(ProductoEntity producto, OrigenCosto origen, BigDecimal cantidad, BigDecimal valor,
            BigDecimal stockDespues, BigDecimal costoAnterior);

    /**
     * Guarda un cambio directo del costo (ajuste manual, receta, importación).
     * No hace nada si el costo no cambió.
     *
     * @param producto      producto ya con el costo nuevo
     * @param costoAnterior costo antes del cambio
     * @param origen        quién lo cambió
     */
    void registrarCambio(ProductoEntity producto, BigDecimal costoAnterior, OrigenCosto origen);

    /**
     * Historial completo del producto: cambios del promedio y líneas de compra.
     *
     * @param productoId producto
     * @param empresaId  empresa de la sesión
     */
    HistorialCostoProductoDto obtener(Long productoId, Integer empresaId);
}
