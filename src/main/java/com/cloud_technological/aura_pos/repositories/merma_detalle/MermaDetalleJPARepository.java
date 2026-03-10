package com.cloud_technological.aura_pos.repositories.merma_detalle;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.MermaDetalleEntity;
public interface MermaDetalleJPARepository extends JpaRepository<MermaDetalleEntity, Long> {
    List<MermaDetalleEntity> findByMermaId(Long mermaId);
}