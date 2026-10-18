package com.cloud_technological.aura_pos.repositories.productos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ProductoCostoHistorialEntity;

@Repository
public interface ProductoCostoHistorialJPARepository extends JpaRepository<ProductoCostoHistorialEntity, Long> {
}
