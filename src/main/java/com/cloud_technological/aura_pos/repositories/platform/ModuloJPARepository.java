package com.cloud_technological.aura_pos.repositories.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ModuloEntity;

public interface ModuloJPARepository extends JpaRepository<ModuloEntity, Integer> {
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Integer id);
    Optional<ModuloEntity> findById(Integer id);
}
