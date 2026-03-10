package com.cloud_technological.aura_pos.repositories.precios_dinamicos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DescuentoClienteEntity;

public interface DescuentoClienteJPARepository extends JpaRepository<DescuentoClienteEntity, Long> {
    Optional<DescuentoClienteEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    
    List<DescuentoClienteEntity> findByEmpresaIdAndTerceroIdAndActivoTrue(Integer empresaId, Long terceroId);
    
    Optional<DescuentoClienteEntity> findByEmpresaIdAndTerceroIdAndCategoriaIdAndActivoTrue(
            Integer empresaId, Long terceroId, Long categoriaId);
    
    boolean existsByEmpresaIdAndTerceroIdAndCategoriaId(
            Integer empresaId, Long terceroId, Long categoriaId);
    
    boolean existsByEmpresaIdAndTerceroIdAndCategoriaIdAndIdNot(
            Integer empresaId, Long terceroId, Long categoriaId, Long id);
}
