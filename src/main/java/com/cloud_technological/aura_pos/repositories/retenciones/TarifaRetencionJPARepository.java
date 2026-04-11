package com.cloud_technological.aura_pos.repositories.retenciones;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TarifaRetencionEntity;

public interface TarifaRetencionJPARepository extends JpaRepository<TarifaRetencionEntity, Long> {

    Optional<TarifaRetencionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndTipoAndConcepto(Integer empresaId, String tipo, String concepto);

    boolean existsByEmpresaIdAndTipoAndConceptoAndIdNot(Integer empresaId, String tipo, String concepto, Long id);

    List<TarifaRetencionEntity> findByEmpresaIdAndActivoTrueOrderByTipoAscConceptoAsc(Integer empresaId);

    List<TarifaRetencionEntity> findByEmpresaIdAndTipoAndActivoTrue(Integer empresaId, String tipo);
}
