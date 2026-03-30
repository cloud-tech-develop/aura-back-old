package com.cloud_technological.aura_pos.repositories.rutas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.RutaEntity;

@Repository
public interface RutaJPARepository extends JpaRepository<RutaEntity, Long> {
    boolean existsByEmpresaIdAndNombreAndActivoTrue(Long empresaId, String nombre);
    boolean existsByEmpresaIdAndNombreAndActivoTrueAndIdNot(Long empresaId, String nombre, Long id);
    Optional<RutaEntity> findByIdAndActivoTrue(Long id);
}