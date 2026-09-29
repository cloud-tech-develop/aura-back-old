package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Operación del cierre de ejercicio (E8). Dos tipos:
 * PROVISION_RENTA — el contador digita el valor (renta fiscal ≠ contable);
 * su asiento es DB 5405 · CR 2404.
 * TRASLADO — al abrir el año se traslada el saldo de 3605 a 3705; el monto
 * queda con signo (>0 utilidad, <0 pérdida).
 */
@Entity
@Table(name = "cierre_anual")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CierreAnualEntity {

    public static final String TIPO_PROVISION_RENTA = "PROVISION_RENTA";
    public static final String TIPO_TRASLADO = "TRASLADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(length = 300)
    private String detalle;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
