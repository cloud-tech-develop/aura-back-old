package com.cloud_technological.aura_pos.repositories.asistencia_frente;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaFrenteDetalleEntity;

public interface AsistenciaFrenteDetalleJPARepository extends JpaRepository<AsistenciaFrenteDetalleEntity, Long> {

    List<AsistenciaFrenteDetalleEntity> findByAsistenciaFrenteIdAndDeletedAtIsNull(Long asistenciaFrenteId);

    void deleteByAsistenciaFrenteId(Long asistenciaFrenteId);

    /** Detecta al mismo empleado reportado el mismo día en OTRO frente. */
    boolean existsByEmpleadoIdAndFechaAndFrenteIdNotAndDeletedAtIsNull(Long empleadoId, LocalDate fecha, Long frenteId);
}
