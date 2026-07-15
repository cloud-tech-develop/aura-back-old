package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CausacionProgramadaEntity;

public interface CausacionProgramadaJPARepository
        extends JpaRepository<CausacionProgramadaEntity, Long> {

    List<CausacionProgramadaEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);

    Optional<CausacionProgramadaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<CausacionProgramadaEntity> findByActivaTrue();
}
