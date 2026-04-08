package com.cloud_technological.aura_pos.repositories.activos_fijos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ActivoFijoEntity;

public interface ActivoFijoJPARepository extends JpaRepository<ActivoFijoEntity, Long> {

    Optional<ActivoFijoEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    boolean existsByCodigoAndEmpresaIdAndDeletedAtIsNull(String codigo, Integer empresaId);

    boolean existsByCodigoAndEmpresaIdAndIdNotAndDeletedAtIsNull(String codigo, Integer empresaId, Long id);

    List<ActivoFijoEntity> findByEmpresaIdAndEstadoAndDeletedAtIsNull(Integer empresaId, String estado);
}
