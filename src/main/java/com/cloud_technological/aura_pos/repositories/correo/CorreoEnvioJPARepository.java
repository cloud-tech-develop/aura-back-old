package com.cloud_technological.aura_pos.repositories.correo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CorreoEnvioEntity;

/** Regla del proyecto: por JPA solo findById/save/delete; las consultas van en CorreoQueryRepository. */
public interface CorreoEnvioJPARepository extends JpaRepository<CorreoEnvioEntity, Long> {
}
