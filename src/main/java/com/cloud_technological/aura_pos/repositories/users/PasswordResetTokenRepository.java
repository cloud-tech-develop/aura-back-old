package com.cloud_technological.aura_pos.repositories.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.PasswordResetTokenEntity;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByToken(String token);

    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Integer usuarioId);
}
