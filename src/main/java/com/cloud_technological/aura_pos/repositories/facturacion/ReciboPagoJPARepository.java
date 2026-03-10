package com.cloud_technological.aura_pos.repositories.facturacion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ReciboPagoEntity;

public interface ReciboPagoJPARepository extends JpaRepository<ReciboPagoEntity, Long> {
    List<ReciboPagoEntity> findByFacturaId(Long facturaId);
    
    List<ReciboPagoEntity> findByIdAndFacturaId(Long id, Long facturaId);
}
