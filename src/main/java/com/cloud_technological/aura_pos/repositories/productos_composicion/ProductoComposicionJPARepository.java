package com.cloud_technological.aura_pos.repositories.productos_composicion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;

public interface ProductoComposicionJPARepository extends JpaRepository<ProductoComposicionEntity, Long> {
    Optional<ProductoComposicionEntity> findByIdAndProductoPadreEmpresaId(Long id, Integer empresaId);
    boolean existsByProductoPadreIdAndProductoHijoId(Long padreId, Long hijoId);
    List<ProductoComposicionEntity> findByProductoPadreId(Long productoPadreId);
}