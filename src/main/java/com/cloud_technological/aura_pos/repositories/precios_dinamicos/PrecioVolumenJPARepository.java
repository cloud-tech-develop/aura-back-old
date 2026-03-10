package com.cloud_technological.aura_pos.repositories.precios_dinamicos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PrecioVolumenEntity;

public interface PrecioVolumenJPARepository extends JpaRepository<PrecioVolumenEntity, Long> {
    Optional<PrecioVolumenEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    
    List<PrecioVolumenEntity> findByEmpresaIdAndProductoPresentacionIdAndActivoTrue(
            Integer empresaId, Long productoPresentacionId);
    
    Optional<PrecioVolumenEntity> findByEmpresaIdAndProductoPresentacionIdAndCantidadMinimaLessThanEqualAndCantidadMaximaGreaterThanEqualAndActivoTrue(
            Integer empresaId, Long productoPresentacionId, Integer cantidadMinima, Integer cantidadMaxima);
    
    boolean existsByEmpresaIdAndProductoPresentacionIdAndCantidadMinimaAndCantidadMaxima(
            Integer empresaId, Long productoPresentacionId, Integer cantidadMinima, Integer cantidadMaxima);
    
    boolean existsByEmpresaIdAndProductoPresentacionIdAndCantidadMinimaAndCantidadMaximaAndIdNot(
            Integer empresaId, Long productoPresentacionId, Integer cantidadMinima, Integer cantidadMaxima, Long id);
}
