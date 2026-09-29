package com.cloud_technological.aura_pos.repositories.asistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaIncidenciaEntity;

public interface AsistenciaIncidenciaJPARepository extends JpaRepository<AsistenciaIncidenciaEntity, Long> {

    Optional<AsistenciaIncidenciaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<AsistenciaIncidenciaEntity> findByEmpresaIdAndEmpleadoIdAndFechaBetweenOrderByFechaAsc(
            Integer empresaId, Long empleadoId, LocalDate desde, LocalDate hasta);

    List<AsistenciaIncidenciaEntity> findByEmpresaIdAndFechaBetweenOrderByFechaAsc(
            Integer empresaId, LocalDate desde, LocalDate hasta);

    List<AsistenciaIncidenciaEntity> findByAsistenciaDiaId(Long asistenciaDiaId);

    long countByEmpresaIdAndEmpleadoIdAndFechaBetweenAndEstado(
            Integer empresaId, Long empleadoId, LocalDate desde, LocalDate hasta, String estado);

    long countByEmpresaIdAndFechaBetweenAndEstado(
            Integer empresaId, LocalDate desde, LocalDate hasta, String estado);
}
