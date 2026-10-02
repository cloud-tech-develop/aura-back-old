package com.cloud_technological.aura_pos.repositories.contabilidad;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DiferidoEntity;

/** Solo findById/save/delete: las consultas van en DiferidoQueryRepository. */
public interface DiferidoJPARepository extends JpaRepository<DiferidoEntity, Long> {
}
