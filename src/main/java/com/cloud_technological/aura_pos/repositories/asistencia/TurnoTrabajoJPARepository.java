package com.cloud_technological.aura_pos.repositories.asistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TurnoTrabajoEntity;

public interface TurnoTrabajoJPARepository extends JpaRepository<TurnoTrabajoEntity, Long> {
    Optional<TurnoTrabajoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    List<TurnoTrabajoEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);
    List<TurnoTrabajoEntity> findByEmpresaIdAndActivoTrueOrderByNombreAsc(Integer empresaId);
}
