package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExogenaColumnaEntity;

public interface ExogenaColumnaJPARepository extends JpaRepository<ExogenaColumnaEntity, Long> {

    List<ExogenaColumnaEntity> findByFormatoIdOrderByOrdenAsc(Long formatoId);
}
