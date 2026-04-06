package com.cloud_technological.aura_pos.repositories.rutas;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.RutaLocalEntity;

@Repository
public interface RutaLocalJPARepository extends JpaRepository<RutaLocalEntity, Long> {
    List<RutaLocalEntity> findByRutaIdOrderByOrdenAsc(Long rutaId);
    void deleteByRutaId(Long rutaId);
}