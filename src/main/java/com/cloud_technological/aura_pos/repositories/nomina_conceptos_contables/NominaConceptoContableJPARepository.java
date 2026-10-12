package com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaConceptoContableEntity;

/** Solo guardar y borrar; las consultas están en {@link NominaConceptoContableQueryRepository}. */
public interface NominaConceptoContableJPARepository extends JpaRepository<NominaConceptoContableEntity, Long> {
}
