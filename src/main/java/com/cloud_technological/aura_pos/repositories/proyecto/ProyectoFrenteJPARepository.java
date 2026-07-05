package com.cloud_technological.aura_pos.repositories.proyecto;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ProyectoFrenteEntity;

public interface ProyectoFrenteJPARepository extends JpaRepository<ProyectoFrenteEntity, Long> {

    Optional<ProyectoFrenteEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    boolean existsByCodigoAndProyectoIdAndDeletedAtIsNull(String codigo, Long proyectoId);

    boolean existsByCodigoAndProyectoIdAndIdNotAndDeletedAtIsNull(String codigo, Long proyectoId, Long id);
}
