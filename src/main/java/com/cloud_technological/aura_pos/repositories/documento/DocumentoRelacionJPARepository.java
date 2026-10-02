package com.cloud_technological.aura_pos.repositories.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DocumentoRelacionEntity;

/**
 * Persistencia de las relaciones de la cadena documental.
 *
 * <p>Regla del proyecto: por JPA solo findById/save/delete; toda consulta va en
 * {@link DocumentoRelacionQueryRepository}.
 */
public interface DocumentoRelacionJPARepository extends JpaRepository<DocumentoRelacionEntity, Long> {
}
