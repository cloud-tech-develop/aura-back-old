package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.SerialProductoEntity;

public interface SerialProductoJPARepository extends JpaRepository<SerialProductoEntity, Long> {
    Optional<SerialProductoEntity> findBySerial(String serial);
    Optional<SerialProductoEntity> findByIdAndSucursalEmpresaId(Long id, Integer empresaId);
}