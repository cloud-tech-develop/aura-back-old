package com.cloud_technological.aura_pos.repositories.compras;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CompraEntity;

public interface CompraJPARepository extends JpaRepository<CompraEntity, Long> {
    Optional<CompraEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
