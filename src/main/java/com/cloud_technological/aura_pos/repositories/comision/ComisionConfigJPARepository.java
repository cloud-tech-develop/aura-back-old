package com.cloud_technological.aura_pos.repositories.comision;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ComisionConfigEntity;

@Repository
public interface ComisionConfigJPARepository extends JpaRepository<ComisionConfigEntity, Long> {

    Optional<ComisionConfigEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    // Busca la config activa para un producto y empresa (para calcular comisión al vender)
    Optional<ComisionConfigEntity> findByProductoIdAndEmpresaIdAndActivoTrue(Long productoId, Integer empresaId);
}
