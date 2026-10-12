package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;

/** Solo consultas simples; el resto está en {@link NominaDocumentoQueryRepository}. */
public interface NominaDocumentoJPARepository extends JpaRepository<NominaDocumentoEntity, Long> {

    Optional<NominaDocumentoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
