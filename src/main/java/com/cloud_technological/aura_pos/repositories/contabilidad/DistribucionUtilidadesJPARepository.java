package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DistribucionUtilidadesEntity;

public interface DistribucionUtilidadesJPARepository
        extends JpaRepository<DistribucionUtilidadesEntity, Long> {

    Optional<DistribucionUtilidadesEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndAnio(Integer empresaId, Integer anio);

    List<DistribucionUtilidadesEntity> findByEmpresaIdOrderByAnioDesc(Integer empresaId);
}
