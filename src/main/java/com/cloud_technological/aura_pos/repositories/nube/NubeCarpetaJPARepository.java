package com.cloud_technological.aura_pos.repositories.nube;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.nube.NubeCarpetaEntity;

/** Regla del proyecto: por JPA solo findById/save/delete; las consultas van en NubeQueryRepository. */
public interface NubeCarpetaJPARepository extends JpaRepository<NubeCarpetaEntity, Long> {
}
