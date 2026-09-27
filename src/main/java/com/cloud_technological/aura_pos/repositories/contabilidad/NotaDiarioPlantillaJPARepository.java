package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NotaDiarioPlantillaEntity;

public interface NotaDiarioPlantillaJPARepository extends JpaRepository<NotaDiarioPlantillaEntity, Long> {

    List<NotaDiarioPlantillaEntity> findByEmpresaIdAndDeletedAtIsNullOrderByNombreAsc(Integer empresaId);

    Optional<NotaDiarioPlantillaEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    /** Candidatas del programador: activas, recurrentes, de todas las empresas. */
    List<NotaDiarioPlantillaEntity> findByRecurrenteTrueAndActivaTrueAndDeletedAtIsNull();

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndDeletedAtIsNullAndIdNot(Integer empresaId, String nombre, Long id);

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndDeletedAtIsNull(Integer empresaId, String nombre);
}
