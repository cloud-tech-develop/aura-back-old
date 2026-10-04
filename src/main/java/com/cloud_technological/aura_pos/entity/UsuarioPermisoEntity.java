package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Excepción de un usuario sobre lo que le da su perfil (V190). Cada acción en
 * null significa "lo que diga el perfil"; true/false la da o la quita.
 */
@Entity
@Table(name = "usuario_permiso")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioPermisoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "submodulo_id", nullable = false)
    private Long submoduloId;

    private Boolean ver;
    private Boolean crear;
    private Boolean editar;
    private Boolean anular;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (createdAt == null) createdAt = ahora;
        updatedAt = ahora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
