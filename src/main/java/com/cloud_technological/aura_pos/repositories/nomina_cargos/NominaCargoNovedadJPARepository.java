package com.cloud_technological.aura_pos.repositories.nomina_cargos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.NominaCargoNovedadEntity;

/** Escritura de {@link NominaCargoNovedadEntity}: solo findById/save/delete; las consultas van en NominaCargoPlantillaQueryRepository. */
@Repository
public interface NominaCargoNovedadJPARepository extends JpaRepository<NominaCargoNovedadEntity, Long> {
}
