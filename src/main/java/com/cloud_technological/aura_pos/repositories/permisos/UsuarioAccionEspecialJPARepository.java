package com.cloud_technological.aura_pos.repositories.permisos;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.UsuarioAccionEspecialEntity;

/** Regla del proyecto: por JPA solo findById/save/delete; las consultas van en PermisoUsuarioQueryRepository. */
public interface UsuarioAccionEspecialJPARepository extends JpaRepository<UsuarioAccionEspecialEntity, Long> {
}
