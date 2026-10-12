package com.cloud_technological.aura_pos.repositories.contabilidad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.DeclaracionPagoEntity;

/** Solo findById / save / delete: las consultas van en DeclaracionPagoQueryRepository. */
@Repository
public interface DeclaracionPagoJPARepository extends JpaRepository<DeclaracionPagoEntity, Long> {
}
