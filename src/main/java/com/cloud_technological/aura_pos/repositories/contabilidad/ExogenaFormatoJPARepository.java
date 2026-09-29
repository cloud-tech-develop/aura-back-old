package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExogenaFormatoEntity;

public interface ExogenaFormatoJPARepository extends JpaRepository<ExogenaFormatoEntity, Long> {

    List<ExogenaFormatoEntity> findByActivoTrueOrderByCodigoAsc();

    Optional<ExogenaFormatoEntity> findByCodigo(String codigo);
}
