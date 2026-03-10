package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.LoteEntity;

public interface LoteJPARepository extends JpaRepository<LoteEntity, Long> {
    Optional<LoteEntity> findByIdAndSucursalEmpresaId(Long id, Integer empresaId);
    Optional<LoteEntity> findByProductoIdAndSucursalIdAndCodigoLote(Long productoId, Long sucursalId, String codigoLote);
}