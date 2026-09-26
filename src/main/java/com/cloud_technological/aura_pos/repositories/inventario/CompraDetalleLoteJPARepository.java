package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CompraDetalleLoteEntity;

public interface CompraDetalleLoteJPARepository extends JpaRepository<CompraDetalleLoteEntity, Long> {
    List<CompraDetalleLoteEntity> findByCompraDetalleIdOrderByIdAsc(Long compraDetalleId);

    List<CompraDetalleLoteEntity> findByCompraDetalleIdIn(Collection<Long> compraDetalleIds);
}
