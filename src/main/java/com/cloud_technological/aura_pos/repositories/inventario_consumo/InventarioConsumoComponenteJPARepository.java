package com.cloud_technological.aura_pos.repositories.inventario_consumo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.InventarioConsumoComponenteEntity;

public interface InventarioConsumoComponenteJPARepository
        extends JpaRepository<InventarioConsumoComponenteEntity, Long> {

    List<InventarioConsumoComponenteEntity> findByOrigenAndDetalleIdOrderByIdAsc(String origen, Long detalleId);
}
