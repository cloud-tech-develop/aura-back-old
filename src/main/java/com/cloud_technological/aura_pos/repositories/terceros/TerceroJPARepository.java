package com.cloud_technological.aura_pos.repositories.terceros;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.TerceroEntity;

public interface TerceroJPARepository extends JpaRepository<TerceroEntity, Integer> {
    // Buscar uno asegurando que sea de la empresa (Seguridad)
    Optional<TerceroEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /**
     * Terceros que coinciden con un documento dentro de la empresa (V99).
     *
     * <p>Devuelve List, no Optional, a propósito: puede haber duplicados
     * históricos (el mismo NIT cargado dos veces). Quien llame decide qué
     * hacer con la ambigüedad en vez de recibir un resultado arbitrario.
     */
    @Query("""
            SELECT t FROM TerceroEntity t
             WHERE t.empresa.id = :empresaId
               AND t.tipoDocumento = :tipoDocumento
               AND t.numeroDocumento = :numeroDocumento
               AND t.deleted_at IS NULL
             ORDER BY t.id ASC
            """)
    List<TerceroEntity> findByDocumento(@Param("empresaId") Integer empresaId,
                                        @Param("tipoDocumento") String tipoDocumento,
                                        @Param("numeroDocumento") String numeroDocumento);

    // Buscar el tercero que representa la empresa (por NIT, excluyendo proveedores)
    @Query("SELECT t FROM TerceroEntity t WHERE t.empresa.id = :empresaId AND t.numeroDocumento = :nit AND (t.esProveedor IS NULL OR t.esProveedor = false) AND t.deleted_at IS NULL ORDER BY t.id ASC")
    Optional<TerceroEntity> findEmpresaTerceroByNit(@Param("empresaId") Integer empresaId, @Param("nit") String nit);

    /**
     * Terceros con un rol dado (V120). Alimenta el selector de entidades de
     * seguridad social: las EPS/AFP/CCF/ARL son terceros con su rol.
     */
    @Query("""
            SELECT t FROM TerceroEntity t
             WHERE t.empresa.id = :empresaId
               AND t.deleted_at IS NULL
               AND (t.activo IS NULL OR t.activo = true)
               AND EXISTS (SELECT 1 FROM TerceroRolEntity r
                            WHERE r.terceroId = t.id AND r.rol = :rol)
             ORDER BY t.razonSocial, t.nombres
            """)
    List<TerceroEntity> findByRolAndEmpresa(@Param("rol") String rol,
                                            @Param("empresaId") Integer empresaId);
}