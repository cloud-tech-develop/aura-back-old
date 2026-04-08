package com.cloud_technological.aura_pos.repositories.activos_fijos;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DepreciacionPeriodoEntity;

public interface DepreciacionPeriodoJPARepository extends JpaRepository<DepreciacionPeriodoEntity, Long> {

    boolean existsByActivoIdAndPeriodoId(Long activoId, Long periodoId);

    List<DepreciacionPeriodoEntity> findByActivoIdOrderByCalculadoEnDesc(Long activoId);

    List<DepreciacionPeriodoEntity> findByEmpresaIdAndPeriodoId(Integer empresaId, Long periodoId);
}
