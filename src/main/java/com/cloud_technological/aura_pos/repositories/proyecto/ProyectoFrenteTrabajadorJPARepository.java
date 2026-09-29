package com.cloud_technological.aura_pos.repositories.proyecto;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ProyectoFrenteTrabajadorEntity;

public interface ProyectoFrenteTrabajadorJPARepository extends JpaRepository<ProyectoFrenteTrabajadorEntity, Long> {

    Optional<ProyectoFrenteTrabajadorEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    Optional<ProyectoFrenteTrabajadorEntity> findByFrenteIdAndEmpleadoIdAndDeletedAtIsNull(Long frenteId, Long empleadoId);

    boolean existsByFrenteIdAndEmpleadoIdAndEstadoAndDeletedAtIsNull(Long frenteId, Long empleadoId, String estado);
}
