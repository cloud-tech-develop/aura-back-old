package com.cloud_technological.aura_pos.repositories.asistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PeriodoAsistenciaEntity;

public interface PeriodoAsistenciaJPARepository extends JpaRepository<PeriodoAsistenciaEntity, Long> {

    Optional<PeriodoAsistenciaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<PeriodoAsistenciaEntity> findByEmpresaIdOrderByFechaInicioDesc(Integer empresaId);
}
