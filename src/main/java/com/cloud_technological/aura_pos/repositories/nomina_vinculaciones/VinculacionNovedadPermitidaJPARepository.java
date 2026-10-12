package com.cloud_technological.aura_pos.repositories.nomina_vinculaciones;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.VinculacionNovedadPermitidaEntity;

/** Solo consultas simples; el resto está en {@link VinculacionDetalleQueryRepository}. */
public interface VinculacionNovedadPermitidaJPARepository
        extends JpaRepository<VinculacionNovedadPermitidaEntity, Long> {

    Optional<VinculacionNovedadPermitidaEntity> findByIdAndContratoId(Long id, Long contratoId);
}
