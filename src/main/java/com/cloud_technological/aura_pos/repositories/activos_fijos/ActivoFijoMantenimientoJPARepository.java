package com.cloud_technological.aura_pos.repositories.activos_fijos;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ActivoFijoMantenimientoEntity;

/** Solo findById/save/delete: las consultas van en ActivoFijoQueryRepository. */
public interface ActivoFijoMantenimientoJPARepository extends JpaRepository<ActivoFijoMantenimientoEntity, Long> {
}
