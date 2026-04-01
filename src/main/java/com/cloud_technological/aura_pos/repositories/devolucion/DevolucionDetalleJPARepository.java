package com.cloud_technological.aura_pos.repositories.devolucion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DevolucionDetalleEntity;

public interface DevolucionDetalleJPARepository extends JpaRepository<DevolucionDetalleEntity, Long> {
    List<DevolucionDetalleEntity> findByDevolucionId(Long devolucionId);
}
