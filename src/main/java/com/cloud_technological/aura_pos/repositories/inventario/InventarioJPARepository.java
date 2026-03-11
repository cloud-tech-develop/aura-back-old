package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.InventarioEntity;

public interface InventarioJPARepository extends JpaRepository<InventarioEntity, Long> {
    Optional<InventarioEntity> findBySucursalIdAndProductoId(Long sucursalId, Long productoId);
    Optional<InventarioEntity> findByIdAndSucursalEmpresaId(Long id, Integer empresaId);
    
    List<InventarioEntity> findByProductoId(Long productoId);

    List<InventarioEntity> findBySucursalEmpresaId(Integer empresaId);
}
