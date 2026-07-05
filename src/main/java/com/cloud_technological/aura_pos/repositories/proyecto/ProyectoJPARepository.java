package com.cloud_technological.aura_pos.repositories.proyecto;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ProyectoEntity;

public interface ProyectoJPARepository extends JpaRepository<ProyectoEntity, Long> {

    Optional<ProyectoEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    boolean existsByCodigoAndEmpresaIdAndDeletedAtIsNull(String codigo, Integer empresaId);

    boolean existsByCodigoAndEmpresaIdAndIdNotAndDeletedAtIsNull(String codigo, Integer empresaId, Long id);
}
