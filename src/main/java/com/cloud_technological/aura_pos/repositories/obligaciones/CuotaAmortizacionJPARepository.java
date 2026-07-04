package com.cloud_technological.aura_pos.repositories.obligaciones;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CuotaAmortizacionEntity;

public interface CuotaAmortizacionJPARepository extends JpaRepository<CuotaAmortizacionEntity, Long> {
    List<CuotaAmortizacionEntity> findByObligacionIdOrderByNumeroCuotaAsc(Long obligacionId);
    Optional<CuotaAmortizacionEntity> findByIdAndObligacionId(Long id, Long obligacionId);
}
