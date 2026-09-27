package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DocumentoSerialEntity;

public interface DocumentoSerialJPARepository extends JpaRepository<DocumentoSerialEntity, Long> {
    List<DocumentoSerialEntity> findByOrigenAndDetalleIdOrderByIdAsc(String origen, Long detalleId);

    long countBySerialId(Long serialId);
}
