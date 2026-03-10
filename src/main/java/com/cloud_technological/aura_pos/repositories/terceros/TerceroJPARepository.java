package com.cloud_technological.aura_pos.repositories.terceros;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.TerceroEntity;

public interface TerceroJPARepository extends JpaRepository<TerceroEntity, Integer> {
    // Buscar uno asegurando que sea de la empresa (Seguridad)
    Optional<TerceroEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    // Buscar el tercero que representa la empresa (por NIT, excluyendo proveedores)
    @Query("SELECT t FROM TerceroEntity t WHERE t.empresa.id = :empresaId AND t.numeroDocumento = :nit AND (t.esProveedor IS NULL OR t.esProveedor = false) AND t.deleted_at IS NULL ORDER BY t.id ASC")
    Optional<TerceroEntity> findEmpresaTerceroByNit(@Param("empresaId") Integer empresaId, @Param("nit") String nit);
}