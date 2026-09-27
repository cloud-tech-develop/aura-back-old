package com.cloud_technological.aura_pos.repositories.venta_detalle_serial;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.VentaDetalleSerialEntity;

public interface VentaDetalleSerialJPARepository extends JpaRepository<VentaDetalleSerialEntity, Long> {
    List<VentaDetalleSerialEntity> findByVentaDetalleId(Long ventaDetalleId);

    boolean existsBySerialProductoId(Long serialProductoId);

    boolean existsBySerialProductoIdAndVentaDetalleId(Long serialProductoId, Long ventaDetalleId);
}
