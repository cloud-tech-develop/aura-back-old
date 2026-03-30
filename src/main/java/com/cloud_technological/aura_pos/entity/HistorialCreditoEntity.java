package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "historial_credito")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialCreditoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id")
    private TerceroEntity tercero;

    @Column(name = "tipo_evento", length = 30)
    private String tipoEvento;

    @Column(name = "cupo_anterior", precision = 15, scale = 2)
    private BigDecimal cupoAnterior;

    @Column(name = "cupo_nuevo", precision = 15, scale = 2)
    private BigDecimal cupoNuevo;

    @Column(name = "score_anterior")
    private Integer scoreAnterior;

    @Column(name = "score_nuevo")
    private Integer scoreNuevo;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
