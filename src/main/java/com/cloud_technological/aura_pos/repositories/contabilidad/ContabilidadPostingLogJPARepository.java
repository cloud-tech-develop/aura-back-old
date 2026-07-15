package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ContabilidadPostingLogEntity;

public interface ContabilidadPostingLogJPARepository
        extends JpaRepository<ContabilidadPostingLogEntity, Long> {

    List<ContabilidadPostingLogEntity> findTop200ByEmpresaIdOrderByCreatedAtDesc(Integer empresaId);

    List<ContabilidadPostingLogEntity> findTop200ByEmpresaIdAndEstadoOrderByCreatedAtDesc(
            Integer empresaId, String estado);
}
