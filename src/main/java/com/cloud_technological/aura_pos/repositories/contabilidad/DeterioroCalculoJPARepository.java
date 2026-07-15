package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DeterioroCalculoEntity;

public interface DeterioroCalculoJPARepository
        extends JpaRepository<DeterioroCalculoEntity, Long> {

    Optional<DeterioroCalculoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
