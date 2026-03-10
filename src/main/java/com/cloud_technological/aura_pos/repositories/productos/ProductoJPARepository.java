package com.cloud_technological.aura_pos.repositories.productos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ProductoEntity;

public interface ProductoJPARepository extends JpaRepository<ProductoEntity, Long> {
    Optional<ProductoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    
    List<ProductoEntity> findByEmpresaId(Integer empresaId);
}