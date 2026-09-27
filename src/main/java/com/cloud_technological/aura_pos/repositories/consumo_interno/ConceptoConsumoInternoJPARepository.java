package com.cloud_technological.aura_pos.repositories.consumo_interno;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ConceptoConsumoInternoEntity;

public interface ConceptoConsumoInternoJPARepository extends JpaRepository<ConceptoConsumoInternoEntity, Long> {
    Optional<ConceptoConsumoInternoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<ConceptoConsumoInternoEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);

    boolean existsByEmpresaId(Integer empresaId);

    boolean existsByEmpresaIdAndNombreIgnoreCase(Integer empresaId, String nombre);

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(Integer empresaId, String nombre, Long id);
}
