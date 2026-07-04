package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.NominaEntity;

public interface NominaJPARepository extends JpaRepository<NominaEntity, Long> {
    Optional<NominaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    List<NominaEntity> findByPeriodoIdAndEmpresaId(Long periodoId, Integer empresaId);
    boolean existsByEmpleadoIdAndPeriodoId(Long empleadoId, Long periodoId);

    // Provisiones acumuladas por empleado (para consumir el pasivo al pagar prestaciones)
    @Query("""
        SELECT COALESCE(SUM(n.provisionPrima), 0) FROM NominaEntity n
        WHERE n.empresa.id = :empresaId AND n.empleado.id = :empleadoId AND n.estado <> 'ANULADO'
        """)
    BigDecimal sumProvisionPrima(@Param("empresaId") Integer empresaId, @Param("empleadoId") Long empleadoId);

    @Query("""
        SELECT COALESCE(SUM(n.provisionVacaciones), 0) FROM NominaEntity n
        WHERE n.empresa.id = :empresaId AND n.empleado.id = :empleadoId AND n.estado <> 'ANULADO'
        """)
    BigDecimal sumProvisionVacaciones(@Param("empresaId") Integer empresaId, @Param("empleadoId") Long empleadoId);

    @Query("""
        SELECT COALESCE(SUM(n.provisionCesantias), 0) FROM NominaEntity n
        WHERE n.empresa.id = :empresaId AND n.empleado.id = :empleadoId AND n.estado <> 'ANULADO'
        """)
    BigDecimal sumProvisionCesantias(@Param("empresaId") Integer empresaId, @Param("empleadoId") Long empleadoId);

    @Query("""
        SELECT COALESCE(SUM(n.provisionIntCesantias), 0) FROM NominaEntity n
        WHERE n.empresa.id = :empresaId AND n.empleado.id = :empleadoId AND n.estado <> 'ANULADO'
        """)
    BigDecimal sumProvisionIntCesantias(@Param("empresaId") Integer empresaId, @Param("empleadoId") Long empleadoId);
}
