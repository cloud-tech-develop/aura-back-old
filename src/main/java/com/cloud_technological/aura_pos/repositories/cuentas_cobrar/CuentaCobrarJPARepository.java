package com.cloud_technological.aura_pos.repositories.cuentas_cobrar;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;

public interface CuentaCobrarJPARepository extends JpaRepository<CuentaCobrarEntity, Long> {
    Optional<CuentaCobrarEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    Optional<CuentaCobrarEntity> findByNumeroCuenta(String numeroCuenta);
    boolean existsByNumeroCuenta(String numeroCuenta);

    List<CuentaCobrarEntity> findByTerceroIdAndEmpresaIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long terceroId, Integer empresaId);
}
