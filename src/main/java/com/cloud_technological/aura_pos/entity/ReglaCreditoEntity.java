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

    @Column(length = 30)
    private String tipo; // BLOQUEO | ALERTA | AUMENTO_CUPO | REDUCCION_CUPO

    @Column(length = 30)
    private String evento; // AL_VENDER | AL_PAGAR | PERIODICO

    @Column(name = "condicion_json", columnDefinition = "jsonb")
    private String condicionJson;

    @Column(name = "accion_json", columnDefinition = "jsonb")
    private String accionJson;

    private Boolean activo;

    private Integer orden;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (activo == null) activo = true;
        if (orden == null) orden = 1;
    }
}
