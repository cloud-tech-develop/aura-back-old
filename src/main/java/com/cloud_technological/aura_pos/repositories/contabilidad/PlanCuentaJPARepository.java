package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;

public interface PlanCuentaJPARepository extends JpaRepository<PlanCuentaEntity, Long> {

    List<PlanCuentaEntity> findByEmpresaIdOrderByCodigoAsc(Integer empresaId);

    Optional<PlanCuentaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndCodigo(Integer empresaId, String codigo);

    List<PlanCuentaEntity> findByEmpresaIdAndActivaTrue(Integer empresaId);

    Optional<PlanCuentaEntity> findByEmpresaIdAndCodigo(Integer empresaId, String codigo);

    /**
     * Cuentas que pueden elegirse como origen de un pago. Solo auxiliares: una
     * cuenta de agrupación no recibe movimientos, así que ofrecerla en el combo
     * solo produce asientos que el validador rechaza después.
     */
    List<PlanCuentaEntity> findByEmpresaIdAndEsMedioPagoTrueAndActivaTrueAndAuxiliarTrueOrderByCodigoAsc(
            Integer empresaId);
}
