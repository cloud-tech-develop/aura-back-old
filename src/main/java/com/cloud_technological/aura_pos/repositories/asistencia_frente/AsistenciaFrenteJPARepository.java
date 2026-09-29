package com.cloud_technological.aura_pos.repositories.asistencia_frente;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaFrenteEntity;

public interface AsistenciaFrenteJPARepository extends JpaRepository<AsistenciaFrenteEntity, Long> {

    Optional<AsistenciaFrenteEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    Optional<AsistenciaFrenteEntity> findByFrenteIdAndFechaAndDeletedAtIsNull(Long frenteId, LocalDate fecha);
}
