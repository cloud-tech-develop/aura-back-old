package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExogenaLineaEntity;

public interface ExogenaLineaJPARepository extends JpaRepository<ExogenaLineaEntity, Long> {

    List<ExogenaLineaEntity> findByLoteIdOrderByConceptoIdAscValorDesc(Long loteId);

    void deleteByLoteId(Long loteId);
}
