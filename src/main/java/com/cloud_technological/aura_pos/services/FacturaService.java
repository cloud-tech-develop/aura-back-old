package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.facturacion.FacturaDto;

public interface FacturaService {
    
    /**
     * Crea una factura automáticamente desde una venta existente
     * @param ventaId ID de la venta
     * @param empresaId ID de la empresa (del JWT)
     * @param usuarioId ID del usuario (del JWT)
     * @return FacturaDto con los datos de la factura creada
     */
    FacturaDto crearDesdeVenta(Long ventaId, Integer empresaId, Integer usuarioId);
}
