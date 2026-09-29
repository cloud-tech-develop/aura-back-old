package com.cloud_technological.aura_pos.repositories.asistencia_frente;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaAlertaEntity;

public interface AsistenciaAlertaJPARepository extends JpaRepository<AsistenciaAlertaEntity, Long> {

    void deleteByAsistenciaFrenteId(Long asistenciaFrenteId);

    boolean existsByAsistenciaFrenteIdAndNivelAndEstadoAndDeletedAtIsNull(
            Long asistenciaFrenteId, String nivel, String estado);
}
