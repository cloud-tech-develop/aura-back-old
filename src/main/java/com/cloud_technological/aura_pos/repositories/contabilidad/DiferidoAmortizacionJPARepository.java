package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DiferidoAmortizacionEntity;

public interface DiferidoAmortizacionJPARepository
        extends JpaRepository<DiferidoAmortizacionEntity, Long> {

    Optional<DiferidoAmortizacionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByGastoIdAndPeriodo(Long gastoId, String periodo);

    long countByGastoId(Long gastoId);
}
