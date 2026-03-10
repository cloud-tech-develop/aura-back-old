package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.DescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteTableDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenTableDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioVolumenDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface PrecioDinamicoService {

    // ========== PRECIO CLIENTE ==========
    
    PageImpl<PrecioClienteTableDto> listarPreciosCliente(PageableDto<Object> pageable, Integer empresaId);
    
    PrecioClienteDto obtenerPrecioClientePorId(Long id, Integer empresaId);
    
    List<PrecioClienteTableDto> listarPreciosClientePorTercero(Integer empresaId, Long terceroId);
    
    PrecioClienteDto crearPrecioCliente(CreatePrecioClienteDto dto, Integer empresaId);
    
    PrecioClienteDto actualizarPrecioCliente(Long id, UpdatePrecioClienteDto dto, Integer empresaId);
    
    void eliminarPrecioCliente(Long id, Integer empresaId);

    // ========== DESCUENTO CLIENTE ==========
    
    PageImpl<DescuentoClienteDto> listarDescuentosCliente(PageableDto<Object> pageable, Integer empresaId);
    
    DescuentoClienteDto obtenerDescuentoClientePorId(Long id, Integer empresaId);
    
    DescuentoClienteDto crearDescuentoCliente(CreateDescuentoClienteDto dto, Integer empresaId);
    
    DescuentoClienteDto actualizarDescuentoCliente(Long id, UpdateDescuentoClienteDto dto, Integer empresaId);
    
    void eliminarDescuentoCliente(Long id, Integer empresaId);

    // ========== PRECIO VOLUMEN ==========
    
    PageImpl<PrecioVolumenTableDto> listarPreciosVolumen(PageableDto<Object> pageable, Integer empresaId);
    
    PrecioVolumenDto obtenerPrecioVolumenPorId(Long id, Integer empresaId);
    
    List<PrecioVolumenTableDto> listarPreciosVolumenPorProducto(Integer empresaId, Long productoPresentacionId);
    
    PrecioVolumenDto crearPrecioVolumen(CreatePrecioVolumenDto dto, Integer empresaId);
    
    PrecioVolumenDto actualizarPrecioVolumen(Long id, UpdatePrecioVolumenDto dto, Integer empresaId);
    
    void eliminarPrecioVolumen(Long id, Integer empresaId);

    // ========== CÁLCULO DE PRECIOS ==========
    
    /**
     * Calcula el precio final para un producto dado un cliente y cantidad.
     * Prioridad:
     * 1. Precio especial por cliente (PrecioCliente)
     * 2. Descuento por cliente (DescuentoCliente)
     * 3. Precio por volumen (PrecioVolumen)
     * 4. Precio base (retornado si no hay ninguna regla)
     * 
     * @param empresaId ID de la empresa
     * @param terceroId ID del cliente
     * @param productoPresentacionId ID de la presentación del producto
     * @param cantidad Cantidad a comprar
     * @param precioBase Precio base del producto
     * @return Precio final calculado
     */
    BigDecimal calcularPrecio(Integer empresaId, Long terceroId, Long productoPresentacionId, 
            Integer cantidad, BigDecimal precioBase);
}
