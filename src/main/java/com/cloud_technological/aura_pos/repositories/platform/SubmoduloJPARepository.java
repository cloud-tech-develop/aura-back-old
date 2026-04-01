package com.cloud_technological.aura_pos.repositories.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.SubmoduloEntity;

public interface SubmoduloJPARepository extends JpaRepository<SubmoduloEntity, Integer> {
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Integer id);
    boolean existsByModuloId(Integer moduloId);
    Optional<SubmoduloEntity> findById(Integer id);
}
