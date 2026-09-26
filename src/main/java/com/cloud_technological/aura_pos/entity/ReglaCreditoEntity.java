package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "regla_credito")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReglaCreditoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(length = 100)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    @Column(length = 30)
    private String tipo; // AUMENTO_CUPO | REDUCCION_CUPO | SUSPENSION | BLOQUEO | ALERTA

    @Column(length = 30)
    private String evento; // AL_VENDER | AL_PAGAR | PERIODICO

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "condicion_json", columnDefinition = "jsonb")
    private String condicionJson;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "accion_json", columnDefinition = "jsonb")
    private String accionJson;

    /** Una misma regla no vuelve a actuar sobre el cliente antes de estos días (V169). */
    @Column(name = "dias_entre_aplicaciones", nullable = false)
    private Integer diasEntreAplicaciones;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Boolean activo;

    private Integer orden;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (activo == null) activo = true;
        if (orden == null) orden = 1;
        if (diasEntreAplicaciones == null) diasEntreAplicaciones = 30;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
