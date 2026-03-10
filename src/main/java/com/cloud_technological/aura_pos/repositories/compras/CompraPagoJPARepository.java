package com.cloud_technological.aura_pos.repositories.compras;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CompraPagoEntity;

public interface CompraPagoJPARepository extends JpaRepository<CompraPagoEntity, Long> {
    
    List<CompraPagoEntity> findByCompraIdAndActivoTrue(Long compraId);
}
