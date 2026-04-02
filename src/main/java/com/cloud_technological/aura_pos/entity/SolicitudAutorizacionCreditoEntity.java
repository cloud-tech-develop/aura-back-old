package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "solicitud_autorizacion_credito")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudAutorizacionCreditoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id")
    private TerceroEntity tercero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private VentaEntity venta;

    @Column(name = "monto_solicitado", precision = 15, scale = 2)
    private BigDecimal montoSolicitado;

    @Column(name = "cupo_disponible", precision = 15, scale = 2)
    private BigDecimal cupoDisponible;

    @Column(name = "excedente", precision = 15, scale = 2)
    private BigDecimal excedente;

    @Column(length = 20)
    private String estado; // PENDIENTE | APROBADA | RECHAZADA

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprobado_por_id")
    private UsuarioEntity aprobadoPor;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (estado == null) estado = "PENDIENTE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
