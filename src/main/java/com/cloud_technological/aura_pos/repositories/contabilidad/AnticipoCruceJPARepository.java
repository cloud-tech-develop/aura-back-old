package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AnticipoCruceEntity;

public interface AnticipoCruceJPARepository extends JpaRepository<AnticipoCruceEntity, Long> {

    Optional<AnticipoCruceEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<AnticipoCruceEntity> findByAnticipoIdOrderByFechaAsc(Long anticipoId);
}
