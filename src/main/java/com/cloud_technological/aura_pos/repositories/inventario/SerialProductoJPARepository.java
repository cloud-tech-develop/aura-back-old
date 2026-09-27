package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.SerialProductoEntity;

public interface SerialProductoJPARepository extends JpaRepository<SerialProductoEntity, Long> {
    Optional<SerialProductoEntity> findBySerial(String serial);
    boolean existsByProductoIdAndSerialIgnoreCase(Long productoId, String serial);

    long countByProductoIdAndBodegaIdAndEstado(Long productoId, Long bodegaId, String estado);

    /** Un serial disponible en la bodega, por el texto escaneado (para el POS). */
    List<SerialProductoEntity> findByBodegaIdAndEstadoAndSerialIgnoreCase(Long bodegaId, String estado,
            String serial);
    Optional<SerialProductoEntity> findByIdAndSucursalEmpresaId(Long id, Integer empresaId);
}