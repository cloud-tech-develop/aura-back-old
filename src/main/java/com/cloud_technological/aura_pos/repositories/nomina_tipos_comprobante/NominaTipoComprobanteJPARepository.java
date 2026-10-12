package com.cloud_technological.aura_pos.repositories.nomina_tipos_comprobante;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NominaTipoComprobanteEntity;

/** Solo consultas simples; el resto está en {@link NominaTipoComprobanteQueryRepository}. */
public interface NominaTipoComprobanteJPARepository extends JpaRepository<NominaTipoComprobanteEntity, Long> {

    Optional<NominaTipoComprobanteEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
