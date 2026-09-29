package com.cloud_technological.aura_pos.repositories.laboral;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CalendarioLaboralEntity;

public interface CalendarioLaboralJPARepository extends JpaRepository<CalendarioLaboralEntity, Long> {

    List<CalendarioLaboralEntity> findByEmpresaIdAndFechaBetweenAndDeletedAtIsNullOrderByFechaAsc(
            Integer empresaId, LocalDate desde, LocalDate hasta);

    Optional<CalendarioLaboralEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    Optional<CalendarioLaboralEntity> findByEmpresaIdAndFechaAndDeletedAtIsNull(Integer empresaId, LocalDate fecha);
}
