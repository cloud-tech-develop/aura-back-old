package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ReciboCajaAplicacionEntity;

public interface ReciboCajaAplicacionJPARepository extends JpaRepository<ReciboCajaAplicacionEntity, Long> {

    List<ReciboCajaAplicacionEntity> findByReciboCajaIdOrderByIdAsc(Long reciboCajaId);
}
