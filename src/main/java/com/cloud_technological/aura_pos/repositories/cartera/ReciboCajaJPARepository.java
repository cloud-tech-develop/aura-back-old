package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ReciboCajaEntity;

public interface ReciboCajaJPARepository extends JpaRepository<ReciboCajaEntity, Long> {

    Optional<ReciboCajaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
