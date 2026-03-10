package com.cloud_technological.aura_pos.repositories.reglas_descuento;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ReglaDescuentoEntity;

public interface ReglaDescuentoJPARepository extends JpaRepository<ReglaDescuentoEntity, Long> {
    Optional<ReglaDescuentoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}