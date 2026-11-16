package com.cloud_technological.aura_pos.entity.nube;

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

/**
 * Solicitud de más espacio en la nube (V236). La crea el admin de la empresa o de
 * su firma cuando se llena la cuota; la aprueba o rechaza la plataforma (tiene costo).
 * Solo puede haber una PENDIENTE por empresa.
 */
@Entity
@Table(name = "nube_solicitud_espacio")
@Getter
@Setter
@NoArgsConstructor
public class NubeSolicitudEspacioEntity {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "solicitado_por", nullable = false, updatable = false)
    private Integer solicitadoPor;

    @Column(name = "cuota_actual_mb", nullable = false, updatable = false)
    private Integer cuotaActualMb;

    @Column(name = "cuota_solicitada_mb", nullable = false)
    private Integer cuotaSolicitadaMb;

    @Column(nullable = false, length = 15)
    private String estado = PENDIENTE;

    @Column(name = "atendido_por")
    private Integer atendidoPor;

    @Column(name = "atendido_at")
    private LocalDateTime atendidoAt;

    @Column(length = 300)
    private String observacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (estado == null) estado = PENDIENTE;
    }
}
