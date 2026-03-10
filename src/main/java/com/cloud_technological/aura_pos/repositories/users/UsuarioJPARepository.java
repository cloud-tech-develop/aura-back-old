package com.cloud_technological.aura_pos.repositories.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.UsuarioEntity;

public interface UsuarioJPARepository extends JpaRepository<UsuarioEntity, Integer>{
    @Query("SELECT u FROM UsuarioEntity u LEFT JOIN FETCH u.tercero WHERE u.username = :username")
    Optional<UsuarioEntity> findByUsername(@Param("username") String username);
    Optional<UsuarioEntity> findByIdAndEmpresaId(Integer id, Integer empresaId);

    @Query("SELECT u FROM UsuarioEntity u LEFT JOIN FETCH u.tercero WHERE u.empresa.id = :empresaId AND u.rol = 'SUPER_ADMIN'")
    Optional<UsuarioEntity> findSuperAdminByEmpresaId(@Param("empresaId") Integer empresaId);
    boolean existsByUsernameAndIdNot(String username, Integer id);
    boolean existsByUsername(String username);
}
