package com.cloud_technological.aura_pos.repositories.reconteo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ReconteoEntity;

public interface ReconteoJPARepository extends JpaRepository<ReconteoEntity, Long> {
    Optional<ReconteoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
