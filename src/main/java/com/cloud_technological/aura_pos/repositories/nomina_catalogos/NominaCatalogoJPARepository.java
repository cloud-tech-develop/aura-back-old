package com.cloud_technological.aura_pos.repositories.nomina_catalogos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaCatalogoEntity;

/** Solo consultas simples; el resto está en {@link NominaCatalogoQueryRepository}. */
public interface NominaCatalogoJPARepository extends JpaRepository<NominaCatalogoEntity, Long> {

    Optional<NominaCatalogoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
