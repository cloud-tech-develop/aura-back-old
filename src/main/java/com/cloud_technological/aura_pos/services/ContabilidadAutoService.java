package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;

public interface ContabilidadAutoService {
    /** Genera el asiento contable para una venta. Idempotente: no duplica si ya existe. */
    AsientoContableTableDto generarDesdeVenta(Long ventaId, Integer empresaId, Integer usuarioId);

    /** Genera el asiento contable para una compra. Idempotente. */
    AsientoContableTableDto generarDesdeCompra(Long compraId, Integer empresaId, Integer usuarioId);
}
