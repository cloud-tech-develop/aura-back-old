package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CausacionEjecucionEntity;

public interface CausacionEjecucionJPARepository
        extends JpaRepository<CausacionEjecucionEntity, Long> {

    Optional<CausacionEjecucionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByCausacionIdAndPeriodo(Long causacionId, String periodo);
}
