package com.cloud_technological.aura_pos.repositories.obsequio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ObsequioDetalleEntity;

public interface ObsequioDetalleJPARepository extends JpaRepository<ObsequioDetalleEntity, Long> {
    List<ObsequioDetalleEntity> findByObsequioId(Long obsequioId);
}
