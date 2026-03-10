package com.cloud_technological.aura_pos.repositories.precios_listas_productos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ListaPreciosEntity;

public interface ListaPreciosJPARepository extends JpaRepository<ListaPreciosEntity, Long> {
    Optional<ListaPreciosEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}