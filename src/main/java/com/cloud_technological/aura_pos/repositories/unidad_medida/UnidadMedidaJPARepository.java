package com.cloud_technological.aura_pos.repositories.unidad_medida;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.UnidadMedidaEntity;

public interface UnidadMedidaJPARepository extends JpaRepository<UnidadMedidaEntity, Long> {
    Optional<UnidadMedidaEntity> findByIdAndActivoTrue(Long id);
}