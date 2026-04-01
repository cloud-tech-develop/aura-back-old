package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;

public interface PlanCuentaJPARepository extends JpaRepository<PlanCuentaEntity, Long> {

    List<PlanCuentaEntity> findByEmpresaIdOrderByCodigoAsc(Integer empresaId);

    Optional<PlanCuentaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndCodigo(Integer empresaId, String codigo);

    List<PlanCuentaEntity> findByEmpresaIdAndActivaTrue(Integer empresaId);
}
