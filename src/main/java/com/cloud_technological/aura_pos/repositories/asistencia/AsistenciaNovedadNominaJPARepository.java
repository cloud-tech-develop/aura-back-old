package com.cloud_technological.aura_pos.repositories.asistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaNovedadNominaEntity;

public interface AsistenciaNovedadNominaJPARepository extends JpaRepository<AsistenciaNovedadNominaEntity, Long> {

    Optional<AsistenciaNovedadNominaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdOrderByEmpleadoIdAsc(
            Integer empresaId, Long periodoNominaId);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdAndEmpleadoId(
            Integer empresaId, Long periodoNominaId, Long empleadoId);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdAndEmpleadoIdAndEstado(
            Integer empresaId, Long periodoNominaId, Long empleadoId, String estado);

    List<AsistenciaNovedadNominaEntity> findByEmpresaIdAndPeriodoNominaIdAndEstado(
            Integer empresaId, Long periodoNominaId, String estado);
}
