package com.cloud_technological.aura_pos.repositories.merma;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.MermaEntity;

public interface MermaJPARepository extends JpaRepository<MermaEntity, Long> {
    Optional<MermaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}