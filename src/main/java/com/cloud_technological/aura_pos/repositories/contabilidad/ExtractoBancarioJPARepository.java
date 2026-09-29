package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExtractoBancarioEntity;

public interface ExtractoBancarioJPARepository extends JpaRepository<ExtractoBancarioEntity, Long> {

    Optional<ExtractoBancarioEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<ExtractoBancarioEntity> findByEmpresaIdOrderByPeriodoDesc(Integer empresaId);

    List<ExtractoBancarioEntity> findByEmpresaIdAndCuentaBancariaIdOrderByPeriodoDesc(
            Integer empresaId, Long cuentaBancariaId);

    boolean existsByEmpresaIdAndCuentaBancariaIdAndPeriodo(
            Integer empresaId, Long cuentaBancariaId, String periodo);

    /** Extractos del período (yyyy-MM) aún sin conciliar — candado del cierre contable. */
    List<ExtractoBancarioEntity> findByEmpresaIdAndPeriodoAndEstado(
            Integer empresaId, String periodo, String estado);
}
