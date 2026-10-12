package com.cloud_technological.aura_pos.repositories.nomina_retiros;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaRetiroEntity;

/** Solo consultas simples; el resto está en {@link NominaRetiroQueryRepository}. */
public interface NominaRetiroJPARepository extends JpaRepository<NominaRetiroEntity, Long> {

    Optional<NominaRetiroEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
