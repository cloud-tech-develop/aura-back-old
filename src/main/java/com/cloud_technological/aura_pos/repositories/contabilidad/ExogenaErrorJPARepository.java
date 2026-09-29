package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExogenaErrorEntity;

public interface ExogenaErrorJPARepository extends JpaRepository<ExogenaErrorEntity, Long> {

    List<ExogenaErrorEntity> findByLoteIdOrderByTipoAscIdAsc(Long loteId);

    void deleteByLoteId(Long loteId);
}
