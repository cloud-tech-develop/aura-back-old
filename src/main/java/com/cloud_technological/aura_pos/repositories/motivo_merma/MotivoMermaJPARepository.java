package com.cloud_technological.aura_pos.repositories.motivo_merma;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.MotivoMermaEntity;

public interface MotivoMermaJPARepository extends JpaRepository<MotivoMermaEntity, Long> {
    Optional<MotivoMermaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}