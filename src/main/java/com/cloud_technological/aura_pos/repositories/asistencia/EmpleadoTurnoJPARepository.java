package com.cloud_technological.aura_pos.repositories.asistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.EmpleadoTurnoEntity;

public interface EmpleadoTurnoJPARepository extends JpaRepository<EmpleadoTurnoEntity, Long> {

    Optional<EmpleadoTurnoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<EmpleadoTurnoEntity> findByEmpresaIdAndEmpleadoIdOrderByFechaInicioDesc(Integer empresaId, Long empleadoId);

    /**
     * Asignación vigente de un empleado en una fecha dada (activa y dentro del rango).
     */
    @Query("""
        SELECT et FROM EmpleadoTurnoEntity et
        WHERE et.empresa.id = :empresaId
          AND et.empleado.id = :empleadoId
          AND et.activo = true
          AND et.fechaInicio <= :fecha
          AND (et.fechaFin IS NULL OR et.fechaFin >= :fecha)
        ORDER BY et.fechaInicio DESC
        """)
    List<EmpleadoTurnoEntity> findVigentes(@Param("empresaId") Integer empresaId,
                                           @Param("empleadoId") Long empleadoId,
                                           @Param("fecha") LocalDate fecha);
}
