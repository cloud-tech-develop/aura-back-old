package com.cloud_technological.aura_pos.repositories.cuentas_pagar;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CuentaPagarEntity;

public interface CuentaPagarJPARepository extends JpaRepository<CuentaPagarEntity, Long> {
    Optional<CuentaPagarEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /** La cuenta que originó una compra a crédito; la usa la edición. */
    Optional<CuentaPagarEntity> findFirstByCompraIdAndEmpresaId(Long compraId, Integer empresaId);
    Optional<CuentaPagarEntity> findByNumeroCuenta(String numeroCuenta);
    boolean existsByNumeroCuenta(String numeroCuenta);
}
