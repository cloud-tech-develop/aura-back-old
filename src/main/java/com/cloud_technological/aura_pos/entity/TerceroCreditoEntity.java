package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tercero_credito")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerceroCreditoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id")
    private TerceroEntity tercero;

    @Column(name = "cupo_credito_inicial", precision = 15, scale = 2)
    private BigDecimal cupoCreditoInicial;

    @Column(name = "cupo_credito_actual", precision = 15, scale = 2)
    private BigDecimal cupoCreditoActual;

    @Column(name = "plazo_dias")
    private Integer plazoDias;

    @Column(name = "estado_credito", length = 20)
    private String estadoCredito; // ACTIVO | SUSPENDIDO | BLOQUEADO | EN_ESTUDIO

    @Column(name = "nivel_riesgo", length = 20)
    private String nivelRiesgo; // BAJO | MEDIO | ALTO | CRITICO

    @Column(name = "score_crediticio")
    private Integer scoreCrediticio;

    @Column(name = "requiere_autorizacion")
    private Boolean requiereAutorizacion;

    @Column(name = "dias_mora_tolerancia")
    private Integer diasMoraTolerancia;

    @Column(name = "fecha_ultimo_estudio")
    private LocalDateTime fechaUltimoEstudio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (estadoCredito == null) estadoCredito = "ACTIVO";
        if (nivelRiesgo == null) nivelRiesgo = "BAJO";
        if (scoreCrediticio == null) scoreCrediticio = 500;
        if (requiereAutorizacion == null) requiereAutorizacion = false;
        if (diasMoraTolerancia == null) diasMoraTolerancia = 30;
        if (plazoDias == null) plazoDias = 30;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
