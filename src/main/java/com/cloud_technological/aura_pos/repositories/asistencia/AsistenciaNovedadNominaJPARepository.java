package com.cloud_technological.aura_pos.repositories.asistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.AsistenciaNovedadNominaEntity;

public interface AsistenciaNovedadNominaJPARepository extends JpaRepository<AsistenciaNovedadNominaEntity, Long> {

    Optional<AsistenciaNovedadNominaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /**
     * Novedades de asistencia que puede consumir la liquidación de un período, para un empleado.
     * Empareja por la FECHA trabajada (no por el FK rígido de período, que puede haber quedado
     * apuntando a otro período cubriendo la misma fecha) o por período ya reclamado:
     *  - ya ancladas a este período (idempotente al re-liquidar), o
     *  - aún sin consumir (APROBADA) cuya asistencia de frente cae dentro del rango del período.
     * Las ENVIADA_A_NOMINA ancladas a OTRO período no se tocan (ya reclamadas → evita doble pago).
     */
    @Query("SELECT n FROM AsistenciaNovedadNominaEntity n WHERE n.empresa.id = :empresaId "
            + "AND n.empleado.id = :empleadoId AND ( "
            + "  (n.periodoNomina.id = :periodoId AND n.estado IN ('APROBADA','ENVIADA_A_NOMINA')) "
            + "  OR (n.estado = 'APROBADA' AND EXISTS ("
            + "        SELECT 1 FROM AsistenciaFrenteEntity af "
            + "        WHERE af.id = n.asistenciaFrenteId AND af.fecha BETWEEN :inicio AND :fin)) )")
    List<AsistenciaNovedadNominaEntity> findConsumiblesParaPeriodo(
            @Param("empresaId") Integer empresaId, @Param("empleadoId") Long empleadoId,
            @Param("periodoId") Long periodoId, @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdOrderByEmpleadoIdAsc(
            Integer empresaId, Long periodoNominaId);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdAndEmpleadoId(
            Integer empresaId, Long periodoNominaId, Long empleadoId);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdAndEmpleadoIdAndEstado(
            Integer empresaId, Long periodoNominaId, Long empleadoId, String estado);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdAndEstado(
            Integer empresaId, Long periodoNominaId, String estado);
}
