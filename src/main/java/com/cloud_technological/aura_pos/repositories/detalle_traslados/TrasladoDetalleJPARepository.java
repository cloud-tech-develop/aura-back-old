package com.cloud_technological.aura_pos.repositories.detalle_traslados;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TrasladoDetalleEntity;

public interface TrasladoDetalleJPARepository extends JpaRepository<TrasladoDetalleEntity, Long> {
    List<TrasladoDetalleEntity> findByTrasladoId(Long trasladoId);
}