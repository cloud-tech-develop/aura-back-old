package com.cloud_technological.aura_pos.repositories.nomina;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;

@Repository
public interface ContratoLaboralJPARepository extends JpaRepository<ContratoLaboralEntity, Long> {

    Optional<ContratoLaboralEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /** Contratos activos de un empleado. Puede haber varios (multi-vínculo). */
    @Query("""
            SELECT c FROM ContratoLaboralEntity c
             WHERE c.empleado.id = :empleadoId
               AND c.deletedAt IS NULL
               AND c.estado = 'ACTIVO'
             ORDER BY c.esPrincipal DESC, c.fechaInicio DESC
            """)
    List<ContratoLaboralEntity> findActivosByEmpleado(@Param("empleadoId") Long empleadoId);

    /** Cargos ya usados en los contratos de la empresa (para el autocompletar del form). */
    @Query("""
            SELECT DISTINCT c.cargo FROM ContratoLaboralEntity c
             WHERE c.empresa.id = :empresaId
               AND c.deletedAt IS NULL
               AND c.cargo IS NOT NULL AND c.cargo <> ''
             ORDER BY c.cargo
            """)
    List<String> cargosUsados(@Param("empresaId") Integer empresaId);

    /**
     * Contratos a liquidar en un período.
     *
     * <p>Vigente = activo y con fechas que se solapan con el período. Incluye
     * a quien ingresó o se retiró a mitad de mes: se le liquidan los días que
     * trabajó, no cero.
     */
    /**
     * Contratos vigentes en un período para liquidar nómina y PILA.
     *
     * <p>B-09 — <b>excluye PRESTACIÓN DE SERVICIOS</b>: no hay relación laboral, el
     * contratista cotiza su propia seguridad social como independiente y se le paga
     * por cuenta de cobro/compra, no por nómina. Meterlo aquí generaría deducciones,
     * aportes y provisiones indebidos (evidencia de contrato realidad) y lo metería
     * en la PILA de la empresa. El aprendiz SÍ entra (va en PILA con su cotizante).
     */
    @Query("""
            SELECT c FROM ContratoLaboralEntity c
             JOIN FETCH c.empleado e
             LEFT JOIN FETCH e.tercero
             WHERE c.empresa.id = :empresaId
               AND c.deletedAt IS NULL
               AND c.estado = 'ACTIVO'
               AND c.tipoContrato <> 'PRESTACION_SERVICIOS'
               AND c.fechaInicio <= :fechaFin
               AND (c.fechaFin IS NULL OR c.fechaFin >= :fechaInicio)
             ORDER BY c.id
            """)
    List<ContratoLaboralEntity> findVigentesEnPeriodo(@Param("empresaId") Integer empresaId,
                                                      @Param("fechaInicio") LocalDate fechaInicio,
                                                      @Param("fechaFin") LocalDate fechaFin);

    /** Empleados que tienen al menos un contrato ACTIVO (para prestaciones/nómina). */
    @Query("""
            SELECT DISTINCT e FROM ContratoLaboralEntity c
             JOIN c.empleado e
             WHERE c.empresa.id = :empresaId
               AND c.deletedAt IS NULL
               AND c.estado = 'ACTIVO'
               AND e.activo = true
             ORDER BY e.nombres
            """)
    List<com.cloud_technological.aura_pos.entity.EmpleadoEntity> findEmpleadosConContratoActivo(
            @Param("empresaId") Integer empresaId);

    /** Contratos a término fijo por vencer. Alimenta las alertas de renovación. */
    @Query("""
            SELECT c FROM ContratoLaboralEntity c
             WHERE c.empresa.id = :empresaId
               AND c.deletedAt IS NULL
               AND c.estado = 'ACTIVO'
               AND c.fechaFin IS NOT NULL
               AND c.fechaFin BETWEEN :desde AND :hasta
             ORDER BY c.fechaFin
            """)
    List<ContratoLaboralEntity> findPorVencer(@Param("empresaId") Integer empresaId,
                                              @Param("desde") LocalDate desde,
                                              @Param("hasta") LocalDate hasta);
}
