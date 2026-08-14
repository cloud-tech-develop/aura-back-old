package com.cloud_technological.aura_pos.repositories.terceros;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.TerceroRolEntity;

@Repository
public interface TerceroRolJPARepository
        extends JpaRepository<TerceroRolEntity, TerceroRolEntity.TerceroRolId> {

    List<TerceroRolEntity> findByTerceroId(Long terceroId);

    boolean existsByTerceroIdAndRol(Long terceroId, String rol);

    void deleteByTerceroId(Long terceroId);

    /**
     * Alta idempotente de un rol. Evita el round-trip de leer-antes-de-escribir
     * y no revienta si el rol ya existe (la PK es (tercero_id, rol)).
     */
    @Modifying
    @Query(value = """
            INSERT INTO tercero_rol (tercero_id, rol, created_at)
            VALUES (:terceroId, :rol, NOW())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void agregarRol(@Param("terceroId") Long terceroId, @Param("rol") String rol);

    @Modifying
    @Query(value = "DELETE FROM tercero_rol WHERE tercero_id = :terceroId AND rol = :rol",
           nativeQuery = true)
    void quitarRol(@Param("terceroId") Long terceroId, @Param("rol") String rol);
}
