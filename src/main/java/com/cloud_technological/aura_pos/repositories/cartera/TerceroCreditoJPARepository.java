package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TerceroCreditoEntity;

public interface TerceroCreditoJPARepository extends JpaRepository<TerceroCreditoEntity, Long> {
    Optional<TerceroCreditoEntity> findByTerceroIdAndEmpresaId(Long terceroId, Integer empresaId);
    boolean existsByTerceroIdAndEmpresaId(Long terceroId, Integer empresaId);

    List<TerceroCreditoEntity> findByEmpresaId(Integer empresaId);
}
