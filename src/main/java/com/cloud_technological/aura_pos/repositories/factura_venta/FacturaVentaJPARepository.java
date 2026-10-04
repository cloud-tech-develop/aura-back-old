package com.cloud_technological.aura_pos.repositories.factura_venta;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.FacturaVentaEntity;

public interface FacturaVentaJPARepository extends JpaRepository<FacturaVentaEntity, Long> {
}
