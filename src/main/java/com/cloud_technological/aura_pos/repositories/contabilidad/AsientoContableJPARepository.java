package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsientoContableEntity;

public interface AsientoContableJPARepository extends JpaRepository<AsientoContableEntity, Long> {

    Optional<AsientoContableEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByTipoOrigenAndOrigenIdAndEmpresaId(String tipoOrigen, Long origenId, Integer empresaId);
}
