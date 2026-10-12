package com.cloud_technological.aura_pos.repositories.nomina_vacaciones;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaVacacionEntity;

/** Solo consultas simples; el resto está en {@link NominaVacacionQueryRepository}. */
public interface NominaVacacionJPARepository extends JpaRepository<NominaVacacionEntity, Long> {

    Optional<NominaVacacionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
