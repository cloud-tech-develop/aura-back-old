package com.cloud_technological.aura_pos.repositories.cuentas_cobrar;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;

public interface CuentaCobrarJPARepository extends JpaRepository<CuentaCobrarEntity, Long> {
    Optional<CuentaCobrarEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /** La cuenta bloqueada: dos cobros simultáneos no pueden abonar sobre el mismo saldo. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT c FROM CuentaCobrarEntity c WHERE c.id = :id AND c.empresa.id = :empresaId")
    Optional<CuentaCobrarEntity> bloquear(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("empresaId") Integer empresaId);
    Optional<CuentaCobrarEntity> findByNumeroCuenta(String numeroCuenta);
    boolean existsByNumeroCuenta(String numeroCuenta);

    List<CuentaCobrarEntity> findByTerceroIdAndEmpresaIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long terceroId, Integer empresaId);

    Optional<CuentaCobrarEntity> findByVentaIdAndEmpresaId(Long ventaId, Integer empresaId);

    /** Cartera activa de la empresa (deterioro por edades, E6). */
    List<CuentaCobrarEntity> findByEmpresaIdAndEstado(Integer empresaId, String estado);
}
