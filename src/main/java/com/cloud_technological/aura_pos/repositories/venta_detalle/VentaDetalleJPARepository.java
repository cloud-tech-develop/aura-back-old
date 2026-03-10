package com.cloud_technological.aura_pos.repositories.venta_detalle;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;

public interface VentaDetalleJPARepository extends JpaRepository<VentaDetalleEntity, Long> {
    List<VentaDetalleEntity> findByVentaId(Long ventaId);
}