package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExogenaConceptoEntity;

public interface ExogenaConceptoJPARepository extends JpaRepository<ExogenaConceptoEntity, Long> {

    List<ExogenaConceptoEntity> findByFormatoIdOrderByCodigoAsc(Long formatoId);
}
