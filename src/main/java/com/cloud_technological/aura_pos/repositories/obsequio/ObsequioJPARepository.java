package com.cloud_technological.aura_pos.repositories.obsequio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ObsequioEntity;

public interface ObsequioJPARepository extends JpaRepository<ObsequioEntity, Long> {
    Optional<ObsequioEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
