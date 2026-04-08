package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.EmpleadoEntity;

public interface EmpleadoJPARepository extends JpaRepository<EmpleadoEntity, Long> {
    Optional<EmpleadoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    List<EmpleadoEntity> findByEmpresaIdAndActivoTrue(Integer empresaId);
    List<EmpleadoEntity> findByEmpresaIdAndActivoTrueAndCargoIgnoreCase(Integer empresaId, String cargo);
}
