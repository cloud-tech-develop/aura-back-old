package com.cloud_technological.aura_pos.repositories.nomina_cargos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.NominaCargoTopeHorasEntity;

/** Escritura de {@link NominaCargoTopeHorasEntity}: solo findById/save/delete; las consultas van en NominaCargoPlantillaQueryRepository. */
@Repository
public interface NominaCargoTopeHorasJPARepository extends JpaRepository<NominaCargoTopeHorasEntity, Long> {
}
