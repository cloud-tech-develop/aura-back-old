package com.cloud_technological.aura_pos.repositories.devolucion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DevolucionEntity;

public interface DevolucionJPARepository extends JpaRepository<DevolucionEntity, Long> {
    Optional<DevolucionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    Long countByEmpresaId(Integer empresaId);
}
