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

/** Una línea de la bitácora de auditoría (V192). Ver {@code BitacoraService}. */
@Entity
@Table(name = "auditoria_evento")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaEventoEntity {

    public static final String ORIGEN_AUTO = "AUTO";
    public static final String ORIGEN_SERVICIO = "SERVICIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "autorizado_por")
    private Integer autorizadoPor;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = 120)
    private String clave;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(length = 60)
    private String entidad;

    @Column(name = "entidad_id", length = 60)
    private String entidadId;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(columnDefinition = "TEXT")
    private String antes;

    @Column(columnDefinition = "TEXT")
    private String despues;

    @Column(length = 10)
    private String metodo;

    @Column(length = 300)
    private String ruta;

    @Column(length = 64)
    private String ip;

    @Column(nullable = false, length = 10)
    private String origen = ORIGEN_SERVICIO;

    @PrePersist
    void prePersist() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
