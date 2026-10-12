package com.cloud_technological.aura_pos.repositories.causacion_gasto;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CausacionGastoEntity;

public interface CausacionGastoJPARepository extends JpaRepository<CausacionGastoEntity, Long> {

    Optional<CausacionGastoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
