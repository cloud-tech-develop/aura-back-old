package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaEntity;

public interface NominaJPARepository extends JpaRepository<NominaEntity, Long> {
    Optional<NominaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    List<NominaEntity> findByPeriodoIdAndEmpresaId(Long periodoId, Integer empresaId);
    boolean existsByEmpleadoIdAndPeriodoId(Long empleadoId, Long periodoId);
}
