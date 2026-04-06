package com.cloud_technological.aura_pos.repositories.locales;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.LocalEntity;

@Repository
public interface LocalJPARepository extends JpaRepository<LocalEntity, Long> {
    boolean existsByEmpresaIdAndNombreAndActivoTrue(Long empresaId, String nombre);
    boolean existsByEmpresaIdAndNombreAndActivoTrueAndIdNot(Long empresaId, String nombre, Long id);
    Optional<LocalEntity> findByIdAndActivoTrue(Long id);
    List<LocalEntity> findByEmpresaIdAndActivoTrueOrderByNombre(Long empresaId);
}