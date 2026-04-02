package com.cloud_technological.aura_pos.repositories.ordenes_compra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.OrdenCompraDetalleEntity;

public interface OrdenCompraDetalleJPARepository extends JpaRepository<OrdenCompraDetalleEntity, Long> {

    List<OrdenCompraDetalleEntity> findByOrdenCompraId(Long ordenCompraId);

    Optional<OrdenCompraDetalleEntity> findByIdAndOrdenCompraId(Long id, Long ordenCompraId);
}
