package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Excepción de un usuario sobre una acción especial de su perfil (V192). */
@Entity
@Table(name = "usuario_accion_especial")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioAccionEspecialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "accion_id", nullable = false)
    private Long accionId;

    @Column(nullable = false)
    private Boolean permitido;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
