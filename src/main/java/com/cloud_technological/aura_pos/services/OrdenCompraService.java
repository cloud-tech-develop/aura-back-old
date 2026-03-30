package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.ordenes_compra.CreateOrdenCompraDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.OrdenCompraDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.OrdenCompraTableDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.RecepcionOrdenDto;

public interface OrdenCompraService {

    List<OrdenCompraTableDto> listar(Integer empresaId);

    OrdenCompraDto obtenerPorId(Long id, Integer empresaId);

    OrdenCompraDto crear(CreateOrdenCompraDto dto, Integer empresaId, Long usuarioId);

    OrdenCompraDto actualizar(Long id, CreateOrdenCompraDto dto, Integer empresaId);

    /** BORRADOR → ENVIADA */
    OrdenCompraDto enviar(Long id, Integer empresaId);

    /** ENVIADA → CONFIRMADA */
    OrdenCompraDto confirmar(Long id, Integer empresaId);

    /**
     * Registra las cantidades recibidas en cada línea de la OC y actualiza el estado
     * (RECIBIDA_PARCIAL o CERRADA). NO crea la compra; el frontend abrirá el formulario
     * de compras pre-llenado para que el usuario complete IVA, fletes, retenciones, etc.
     */
    OrdenCompraDto recibirMercancia(Long id, RecepcionOrdenDto dto, Integer empresaId);

    /** Cualquier estado no cerrado → ANULADA */
    void anular(Long id, Integer empresaId);
}
