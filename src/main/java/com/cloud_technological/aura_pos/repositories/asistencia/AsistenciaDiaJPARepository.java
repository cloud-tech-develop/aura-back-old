package com.cloud_technological.aura_pos.repositories.asistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaDiaEntity;

public interface AsistenciaDiaJPARepository extends JpaRepository<AsistenciaDiaEntity, Long> {

    Optional<AsistenciaDiaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    Optional<AsistenciaDiaEntity> findByEmpresaIdAndEmpleadoIdAndFecha(
            Integer empresaId, Long empleadoId, LocalDate fecha);

    List<AsistenciaDiaEntity> findByEmpresaIdAndEmpleadoIdAndFechaBetweenOrderByFechaAsc(
            Integer empresaId, Long empleadoId, LocalDate desde, LocalDate hasta);

    List<AsistenciaDiaEntity> findByEmpresaIdAndFechaBetween(
            Integer empresaId, LocalDate desde, LocalDate hasta);
}
