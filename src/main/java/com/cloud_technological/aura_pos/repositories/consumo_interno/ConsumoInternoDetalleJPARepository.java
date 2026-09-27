package com.cloud_technological.aura_pos.repositories.consumo_interno;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ConsumoInternoDetalleEntity;

public interface ConsumoInternoDetalleJPARepository extends JpaRepository<ConsumoInternoDetalleEntity, Long> {
    List<ConsumoInternoDetalleEntity> findByConsumoInternoId(Long consumoInternoId);
}
