package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Membresía/suscripción de un cliente (empresa) del platform. */
@Entity
@Table(name = "empresa_suscripcion")
@Getter
@Setter
public class EmpresaSuscripcionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** UNICO | MENSUAL */
    @Column(name = "tipo_plan", nullable = false, length = 20)
    private String tipoPlan = "MENSUAL";

    /** PRUEBA | ACTIVA | SUSPENDIDA | CANCELADA (VENCIDA se calcula). */
    @Column(nullable = false, length = 20)
    private String estado = "ACTIVA";

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String moneda = "COP";

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_proximo_pago")
    private LocalDate fechaProximoPago;

    @Column(name = "dia_cobro")
    private Integer diaCobro;

    @Column(name = "contacto_nombre", length = 150)
    private String contactoNombre;

    @Column(name = "contacto_email", length = 150)
    private String contactoEmail;

    @Column(name = "contacto_telefono", length = 40)
    private String contactoTelefono;

    @Column(columnDefinition = "text")
    private String notas;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
