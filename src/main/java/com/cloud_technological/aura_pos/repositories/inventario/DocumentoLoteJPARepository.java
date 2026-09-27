package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DocumentoLoteEntity;

public interface DocumentoLoteJPARepository extends JpaRepository<DocumentoLoteEntity, Long> {
    List<DocumentoLoteEntity> findByOrigenAndDetalleIdOrderByIdAsc(String origen, Long detalleId);

    List<DocumentoLoteEntity> findByOrigenAndDetalleIdIn(String origen, Collection<Long> detalleIds);
}
