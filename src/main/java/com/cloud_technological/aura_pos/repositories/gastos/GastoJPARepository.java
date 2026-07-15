package com.cloud_technological.aura_pos.repositories.gastos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.GastoEntity;

public interface GastoJPARepository extends JpaRepository<GastoEntity, Long> {
    Optional<GastoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /** Gastos diferidos pendientes de amortizar (E6). */
    java.util.List<GastoEntity> findByEsDiferidoTrue();
}
