package com.cloud_technological.aura_pos.repositories.factura_venta;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.FacturaVentaDetalleEntity;

public interface FacturaVentaDetalleJPARepository extends JpaRepository<FacturaVentaDetalleEntity, Long> {
}
