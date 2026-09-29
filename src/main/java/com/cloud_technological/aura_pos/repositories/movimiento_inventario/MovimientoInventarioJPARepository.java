package com.cloud_technological.aura_pos.repositories.movimiento_inventario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;

public interface MovimientoInventarioJPARepository extends JpaRepository<MovimientoInventarioEntity, Long> {
    List<MovimientoInventarioEntity> findByProductoIdAndSucursalIdOrderByCreatedAtDesc(Long productoId, Long sucursalId);
}