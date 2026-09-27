package com.cloud_technological.aura_pos.repositories.consumo_interno;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ConsumoInternoEntity;

public interface ConsumoInternoJPARepository extends JpaRepository<ConsumoInternoEntity, Long> {
    Optional<ConsumoInternoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
