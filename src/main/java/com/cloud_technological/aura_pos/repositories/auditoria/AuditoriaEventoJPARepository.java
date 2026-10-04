package com.cloud_technological.aura_pos.repositories.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AuditoriaEventoEntity;

/** Regla del proyecto: por JPA solo findById/save/delete; las consultas van en BitacoraQueryRepository. */
public interface AuditoriaEventoJPARepository extends JpaRepository<AuditoriaEventoEntity, Long> {
}
