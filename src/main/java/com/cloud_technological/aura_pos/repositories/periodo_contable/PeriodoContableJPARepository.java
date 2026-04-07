package com.cloud_technological.aura_pos.repositories.periodo_contable;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;

public interface PeriodoContableJPARepository extends JpaRepository<PeriodoContableEntity, Long> {

    Optional<PeriodoContableEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    Optional<PeriodoContableEntity> findByEmpresaIdAndEstado(Integer empresaId, String estado);

    boolean existsByEmpresaIdAndAnioAndMes(Integer empresaId, Short anio, Short mes);

    boolean existsByEmpresaIdAndEstado(Integer empresaId, String estado);
}
