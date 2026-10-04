package com.cloud_technological.aura_pos.repositories.permisos;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PerfilEntity;

/** Regla del proyecto: por JPA solo findById/save/delete; las consultas van en PermisoUsuarioQueryRepository. */
public interface PerfilJPARepository extends JpaRepository<PerfilEntity, Long> {
}
