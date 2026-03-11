package com.cloud_technological.aura_pos.repositories.comision;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ComisionLiquidacionEntity;

@Repository
public interface ComisionLiquidacionJPARepository extends JpaRepository<ComisionLiquidacionEntity, Long> {

    Optional<ComisionLiquidacionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
