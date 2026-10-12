package com.cloud_technological.aura_pos.repositories.nomina_incrementos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaIncrementoEntity;

/** Solo consultas simples; el resto está en {@link NominaIncrementoQueryRepository}. */
public interface NominaIncrementoJPARepository extends JpaRepository<NominaIncrementoEntity, Long> {

    Optional<NominaIncrementoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
