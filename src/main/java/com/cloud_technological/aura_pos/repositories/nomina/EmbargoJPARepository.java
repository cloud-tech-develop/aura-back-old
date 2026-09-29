package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.EmbargoEntity;

/** Embargos sobre el salario (V113). */
@Repository
public interface EmbargoJPARepository extends JpaRepository<EmbargoEntity, Long> {

    List<EmbargoEntity> findByContratoIdOrderByPrioridadAsc(Long contratoId);

    Optional<EmbargoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
