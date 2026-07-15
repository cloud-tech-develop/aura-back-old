package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Línea del extracto bancario (E9). El valor lleva el signo del banco:
 * &gt;0 abono (entra dinero) / &lt;0 cargo. PENDIENTE → CONCILIADO cuando se
 * confirma el match con un asiento_detalle del libro, o AJUSTE cuando la
 * línea no existía en el libro y genera su propio asiento (comisión, GMF,
 * intereses) desde la pantalla de conciliación.
 */
@Entity
@Table(name = "extracto_linea")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractoLineaEntity {

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_CONCILIADO = "CONCILIADO";
    public static final String ESTADO_AJUSTE = "AJUSTE";

    public static final String AJUSTE_GASTO_BANCARIO = "GASTO_BANCARIO";
    public static final String AJUSTE_GMF = "GMF";
    public static final String AJUSTE_INTERES = "INTERES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extracto_id", nullable = false)
    private Long extractoId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 15)
    @Builder.Default
    private String estado = ESTADO_PENDIENTE;

    /** Detalle del libro con el que se confirmó el match. */
    @Column(name = "asiento_detalle_id")
    private Long asientoDetalleId;

    /** GASTO_BANCARIO | GMF | INTERES cuando la línea se contabilizó como ajuste. */
    @Column(name = "tipo_ajuste", length = 20)
    private String tipoAjuste;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
