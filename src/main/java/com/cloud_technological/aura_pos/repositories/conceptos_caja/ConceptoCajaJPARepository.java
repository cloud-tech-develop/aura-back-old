package com.cloud_technological.aura_pos.repositories.conceptos_caja;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ConceptoCajaEntity;

public interface ConceptoCajaJPARepository extends JpaRepository<ConceptoCajaEntity, Long> {

    List<ConceptoCajaEntity> findByEmpresaIdAndActivoTrueOrderByNombreAsc(Integer empresaId);

    List<ConceptoCajaEntity> findByEmpresaIdAndTipoAndActivoTrueOrderByNombreAsc(Integer empresaId, String tipo);

    Optional<ConceptoCajaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndTipoAndNombreIgnoreCaseAndActivoTrue(Integer empresaId, String tipo, String nombre);
}
