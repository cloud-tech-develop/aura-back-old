package com.cloud_technological.aura_pos.repositories.servicio_tecnico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenEntity;

/** Solo findById/save/delete: las consultas van en {@link ServicioTecnicoQueryRepository}. */
@Repository
public interface ServicioOrdenJPARepository extends JpaRepository<ServicioOrdenEntity, Long> {
}
