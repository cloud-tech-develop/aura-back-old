package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExogenaMapeoCuentaEntity;

public interface ExogenaMapeoCuentaJPARepository extends JpaRepository<ExogenaMapeoCuentaEntity, Long> {

    List<ExogenaMapeoCuentaEntity> findByEmpresaId(Integer empresaId);

    List<ExogenaMapeoCuentaEntity> findByEmpresaIdAndConceptoIdIn(
            Integer empresaId, Collection<Long> conceptoIds);

    Optional<ExogenaMapeoCuentaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaId(Integer empresaId);
}
