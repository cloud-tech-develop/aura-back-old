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

    /** @deprecated Usar {@link #findByContratoIdAndPeriodoId}. Con multi-vínculo, un empleado puede tener varias nóminas por período. */
    @Deprecated
    boolean existsByEmpleadoIdAndPeriodoId(Long empleadoId, Long periodoId);

    // ── V103: la nómina se liquida contra un CONTRATO ───────────────────────

    Optional<NominaEntity> findByContratoIdAndPeriodoId(Long contratoId, Long periodoId);

    boolean existsByContratoIdAndPeriodoId(Long contratoId, Long periodoId);

    /**
     * Nóminas liquidadas de un contrato cuyo período cae en un rango (el mes de
     * PILA). Con períodos quincenales pueden ser dos; se agregan al armar el
     * cotizante. Excluye anuladas.
     */
    @Query("""
            SELECT n FROM NominaEntity n
             WHERE n.contrato.id = :contratoId
               AND n.periodo.fechaInicio <= :hasta
               AND n.periodo.fechaFin >= :desde
               AND n.estado IN ('APROBADO', 'PAGADO')
             ORDER BY n.periodo.fechaInicio
            """)
    List<NominaEntity> findByContratoEnRango(@Param("contratoId") Long contratoId,
                                             @Param("desde") java.time.LocalDate desde,
                                             @Param("hasta") java.time.LocalDate hasta);

    @Query("SELECT COUNT(n) FROM NominaEntity n WHERE n.contrato IS NULL")
    long countSinContrato();

    /**
     * F3 — días de vacaciones ya tomados por un empleado (novedades VACACIONES de
     * sus nóminas no anuladas). Sirve para calcular el saldo disponible.
     */
    @Query("""
            SELECT COALESCE(SUM(nv.dias), 0)
              FROM NominaEntity n JOIN n.novedades nv
             WHERE n.empresa.id = :empresaId
               AND n.empleado.id = :empleadoId
               AND n.estado <> 'ANULADO'
               AND nv.tipo = 'VACACIONES'
            """)
    Long sumDiasVacacionesTomados(@Param("empresaId") Integer empresaId,
                                  @Param("empleadoId") Long empleadoId);

    // Provisiones acumuladas por empleado (para consumir el pasivo al pagar prestaciones)
    //
    // ⚠️ DEUDA CONOCIDA (V103): estas sumas son POR EMPLEADO. Con multi-vínculo
    //    activo, un empleado con dos contratos vería sumadas las provisiones de
    //    ambos, y al pagar la prima de uno se consumiría el pasivo del otro.
    //    Deben pasar a agrupar por contrato_id cuando la Fase 8 (prestaciones)
    //    se implemente. Hoy no hace daño: no hay multi-vínculo en uso.
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
