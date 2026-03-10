package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "error_log")
@Getter
@Setter
public class ErrorLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String metodo;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(nullable = false, length = 10)
    private String categoria;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "grupo_hash", nullable = false, length = 64)
    private String grupoHash;

    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(name = "usuario_nombre", length = 200)
    private String usuarioNombre;

    @Column(name = "ip_origen", length = 50)
    private String ipOrigen;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
