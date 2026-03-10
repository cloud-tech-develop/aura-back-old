package com.cloud_technological.aura_pos.repositories.detalle_compras;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CompraDetalleEntity;

public interface CompraDetalleJPARepository extends JpaRepository<CompraDetalleEntity, Long> {
    List<CompraDetalleEntity> findByCompraId(Long compraId);
}
