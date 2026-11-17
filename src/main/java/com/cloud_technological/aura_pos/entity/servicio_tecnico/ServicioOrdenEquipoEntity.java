package com.cloud_technological.aura_pos.entity.servicio_tecnico;

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

/** Equipo recibido en una orden (la card): marca, modelo, IMEI, color, accesorios y problemas (V249). */
@Entity
@Table(name = "servicio_orden_equipo")
@Getter
@Setter
@NoArgsConstructor
public class ServicioOrdenEquipoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "orden_id", nullable = false, updatable = false)
    private Long ordenId;

    @Column(nullable = false, length = 20)
    private String tipo = "CELULAR";

    @Column(nullable = false, length = 60)
    private String marca;

    @Column(nullable = false, length = 80)
    private String modelo;

    @Column(length = 20)
    private String imei;

    @Column(length = 20)
    private String imei2;

    @Column(length = 60)
    private String serial;

    @Column(length = 40)
    private String color;

    /** Accesorios recibidos separados por "|". */
    @Column(length = 300)
    private String accesorios;

    /** Estado físico al recibirlo separado por "|". */
    @Column(name = "estado_fisico", length = 300)
    private String estadoFisico;

    private Boolean enciende;

    @Column(name = "problemas_reportados", nullable = false, columnDefinition = "TEXT")
    private String problemasReportados;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
