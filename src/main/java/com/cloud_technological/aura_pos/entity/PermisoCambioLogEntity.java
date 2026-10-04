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

/** Quién cambió qué permiso y cuándo (V190). */
@Entity
@Table(name = "permiso_cambio_log")
@Getter
@Setter
@NoArgsConstructor
public class PermisoCambioLogEntity {

    public static final String TIPO_PERFIL = "PERFIL";
    public static final String TIPO_USUARIO = "USUARIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** Quien hizo el cambio. */
    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "perfil_id")
    private Long perfilId;

    @Column(name = "usuario_afectado_id")
    private Integer usuarioAfectadoId;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
