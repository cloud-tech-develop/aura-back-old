package com.cloud_technological.aura_pos.repositories.marcas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.MarcaEntity;

public interface MarcaJPARepository extends JpaRepository<MarcaEntity, Long> {
    Optional<MarcaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}