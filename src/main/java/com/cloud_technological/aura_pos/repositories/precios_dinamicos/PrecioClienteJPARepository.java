package com.cloud_technological.aura_pos.repositories.precios_dinamicos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PrecioClienteEntity;

public interface PrecioClienteJPARepository extends JpaRepository<PrecioClienteEntity, Long> {
    Optional<PrecioClienteEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    
    List<PrecioClienteEntity> findByEmpresaIdAndTerceroIdAndActivoTrue(Integer empresaId, Long terceroId);
    
    Optional<PrecioClienteEntity> findByEmpresaIdAndTerceroIdAndProductoPresentacionIdAndActivoTrue(
            Integer empresaId, Long terceroId, Long productoPresentacionId);
    
    boolean existsByEmpresaIdAndTerceroIdAndProductoPresentacionId(
            Integer empresaId, Long terceroId, Long productoPresentacionId);
    
    boolean existsByEmpresaIdAndTerceroIdAndProductoPresentacionIdAndIdNot(
            Integer empresaId, Long terceroId, Long productoPresentacionId, Long id);
}
