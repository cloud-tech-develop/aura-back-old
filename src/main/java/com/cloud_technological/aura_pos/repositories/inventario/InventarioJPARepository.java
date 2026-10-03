package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.InventarioEntity;

public interface InventarioJPARepository extends JpaRepository<InventarioEntity, Long> {

    // El saldo (bodega, producto) se toma con InventarioStockService.bloquear,
    // que además lo bloquea contra movimientos simultáneos. El saldo vive en la
    // bodega desde V172: una sucursal con dos bodegas tiene dos filas del mismo
    // producto.

    Optional<InventarioEntity> findByIdAndSucursalEmpresaId(Long id, Integer empresaId);

    /** Todas las bodegas de una sucursal para ese producto. */
    List<InventarioEntity> findBySucursalIdAndProductoId(Long sucursalId, Long productoId);

    List<InventarioEntity> findByProductoId(Long productoId);

    List<InventarioEntity> findBySucursalEmpresaId(Integer empresaId);

    List<InventarioEntity> findByBodegaId(Long bodegaId);
}
