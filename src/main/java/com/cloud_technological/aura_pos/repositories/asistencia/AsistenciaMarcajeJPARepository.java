package com.cloud_technological.aura_pos.repositories.asistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaMarcajeEntity;

public interface AsistenciaMarcajeJPARepository extends JpaRepository<AsistenciaMarcajeEntity, Long> {

    Optional<AsistenciaMarcajeEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<AsistenciaMarcajeEntity> findByEmpresaIdAndEmpleadoIdAndFechaAndEstadoOrderByFechaHoraMarcajeAsc(
            Integer empresaId, Long empleadoId, LocalDate fecha, String estado);

    List<AsistenciaMarcajeEntity> findByEmpresaIdAndEmpleadoIdAndFechaOrderByFechaHoraMarcajeAsc(
            Integer empresaId, Long empleadoId, LocalDate fecha);
}
