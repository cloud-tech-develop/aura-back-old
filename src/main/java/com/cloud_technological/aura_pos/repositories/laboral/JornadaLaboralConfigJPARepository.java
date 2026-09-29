package com.cloud_technological.aura_pos.repositories.laboral;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.JornadaLaboralConfigEntity;

public interface JornadaLaboralConfigJPARepository extends JpaRepository<JornadaLaboralConfigEntity, Long> {

    List<JornadaLaboralConfigEntity> findByEmpresaIdAndDeletedAtIsNullOrderByFechaInicioVigenciaDesc(Integer empresaId);

    Optional<JornadaLaboralConfigEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);
}
