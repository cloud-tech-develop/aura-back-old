package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CierreAnualEntity;

public interface CierreAnualJPARepository
        extends JpaRepository<CierreAnualEntity, Long> {

    Optional<CierreAnualEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndAnioAndTipo(Integer empresaId, Integer anio, String tipo);

    List<CierreAnualEntity> findByEmpresaIdOrderByAnioDescCreatedAtDesc(Integer empresaId);
}
