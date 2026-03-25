package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;

public interface PeriodoNominaJPARepository extends JpaRepository<PeriodoNominaEntity, Long> {
    Optional<PeriodoNominaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    List<PeriodoNominaEntity> findByEmpresaIdOrderByIdDesc(Integer empresaId);
}
