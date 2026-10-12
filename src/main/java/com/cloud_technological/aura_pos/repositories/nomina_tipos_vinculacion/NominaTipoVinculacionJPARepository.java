package com.cloud_technological.aura_pos.repositories.nomina_tipos_vinculacion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaTipoVinculacionEntity;

/** Solo consultas simples; el resto está en {@link NominaTipoVinculacionQueryRepository}. */
public interface NominaTipoVinculacionJPARepository extends JpaRepository<NominaTipoVinculacionEntity, Long> {

    Optional<NominaTipoVinculacionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
