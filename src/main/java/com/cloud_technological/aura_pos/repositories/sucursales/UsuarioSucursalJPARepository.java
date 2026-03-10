package com.cloud_technological.aura_pos.repositories.sucursales;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.UsuarioSucursalEntity;

public interface UsuarioSucursalJPARepository extends JpaRepository<UsuarioSucursalEntity, Integer> {
    @Modifying
    @Query("DELETE FROM UsuarioSucursalEntity us WHERE us.usuario.id = :usuarioId")
    void deleteAllByUsuarioId(@Param("usuarioId") Integer usuarioId);

    List<UsuarioSucursalEntity> findAllByUsuarioId(Integer usuarioId);

    // void deleteAllByUsuarioId(Integer usuarioId);
}