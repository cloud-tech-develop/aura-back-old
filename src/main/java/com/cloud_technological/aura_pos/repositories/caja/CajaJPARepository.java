package com.cloud_technological.aura_pos.repositories.caja;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CajaEntity;

public interface CajaJPARepository extends JpaRepository<CajaEntity, Long>{
    Optional<CajaEntity> findByIdAndSucursalEmpresaId(Long id, Integer empresaId);
}
