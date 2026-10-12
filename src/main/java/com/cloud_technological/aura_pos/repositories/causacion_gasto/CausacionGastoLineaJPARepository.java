package com.cloud_technological.aura_pos.repositories.causacion_gasto;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CausacionGastoLineaEntity;

/** Solo para grabar: las líneas se leen en CausacionGastoQueryRepository. */
public interface CausacionGastoLineaJPARepository extends JpaRepository<CausacionGastoLineaEntity, Long> {
}
