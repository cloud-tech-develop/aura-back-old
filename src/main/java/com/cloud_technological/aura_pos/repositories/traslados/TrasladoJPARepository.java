package com.cloud_technological.aura_pos.repositories.traslados;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TrasladoEntity;

public interface TrasladoJPARepository extends JpaRepository<TrasladoEntity, Long> {
    Optional<TrasladoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
