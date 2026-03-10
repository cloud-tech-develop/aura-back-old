package com.cloud_technological.aura_pos.repositories.venta_pago;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.VentaPagoEntity;

public interface VentaPagoJPARepository extends JpaRepository<VentaPagoEntity, Long> {
    List<VentaPagoEntity> findByVentaId(Long ventaId);
}