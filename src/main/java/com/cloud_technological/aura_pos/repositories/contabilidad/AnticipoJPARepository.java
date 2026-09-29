package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AnticipoEntity;

public interface AnticipoJPARepository extends JpaRepository<AnticipoEntity, Long> {

    Optional<AnticipoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<AnticipoEntity> findByEmpresaIdOrderByFechaDescIdDesc(Integer empresaId);

    List<AnticipoEntity> findByEmpresaIdAndTerceroIdAndEstadoOrderByFechaAsc(
            Integer empresaId, Long terceroId, String estado);
}
