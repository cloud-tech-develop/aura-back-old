package com.cloud_technological.aura_pos.repositories.cuentas_pagar;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CuentaPagarEntity;

public interface CuentaPagarJPARepository extends JpaRepository<CuentaPagarEntity, Long> {
    Optional<CuentaPagarEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    Optional<CuentaPagarEntity> findByNumeroCuenta(String numeroCuenta);
    boolean existsByNumeroCuenta(String numeroCuenta);
}
