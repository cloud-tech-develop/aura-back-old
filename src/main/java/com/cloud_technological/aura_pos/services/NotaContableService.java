package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.util.List;

import com.cloud_technological.aura_pos.dto.facturacion.NotaContableDto;

public interface NotaContableService {
    
    NotaContableDto crear(NotaContableDto dto, Integer empresaId, Integer usuarioId);
    
    List<NotaContableDto> obtenerPorFactura(Long facturaId, Integer empresaId);
    
    NotaContableDto generarNotaCreditoVuelto(Long facturaId, BigDecimal monto, Integer usuarioId, String metodoPago, String descripcion);
    
    NotaContableDto generarNotaDebitoPago(Long facturaId, BigDecimal monto, Integer usuarioId, String metodoPago, String descripcion);
    
    NotaContableDto generarNotaDebitoPagoCompra(Long compraId, BigDecimal monto, Integer usuarioId, String metodoPago, String descripcion);
}
