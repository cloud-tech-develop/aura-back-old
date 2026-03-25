package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaConfigEntity;

public interface NominaConfigJPARepository extends JpaRepository<NominaConfigEntity, Long> {
    Optional<NominaConfigEntity> findByEmpresaId(Integer empresaId);
}
