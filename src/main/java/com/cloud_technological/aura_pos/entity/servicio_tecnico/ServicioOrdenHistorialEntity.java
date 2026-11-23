package com.cloud_technological.aura_pos.entity.servicio_tecnico;

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

/** Cambio de estado o nota de una orden; solo se inserta (V249). */
@Entity
@Table(name = "servicio_orden_historial")
@Getter
@Setter
@NoArgsConstructor
public class ServicioOrdenHistorialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "orden_id", nullable = false, updatable = false)
    private Long ordenId;

    /** ESTADO o NOTA. */
    @Column(nullable = false, length = 10, updatable = false)
    private String tipo;

    @Column(name = "estado_anterior", length = 25, updatable = false)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 25, updatable = false)
    private String estadoNuevo;

    @Column(length = 1000, updatable = false)
    private String nota;

    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
