package com.cloud_technological.aura_pos.repositories.laboral;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.FrenteTurnoEntity;

public interface FrenteTurnoJPARepository extends JpaRepository<FrenteTurnoEntity, Long> {

    List<FrenteTurnoEntity> findByFrenteIdAndDeletedAtIsNull(Long frenteId);

    Optional<FrenteTurnoEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);
}
