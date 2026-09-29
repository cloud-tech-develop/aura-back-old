package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ContabilidadConfigLogEntity;

public interface ContabilidadConfigLogJPARepository
        extends JpaRepository<ContabilidadConfigLogEntity, Long> {

    List<ContabilidadConfigLogEntity> findByEmpresaIdOrderByCreatedAtDesc(Integer empresaId);
}
