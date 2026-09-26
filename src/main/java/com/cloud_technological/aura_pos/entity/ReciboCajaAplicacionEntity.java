package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cuánto de un recibo fue a cada factura. Sobrevive a la anulación (que borra
 * los abonos, como siempre lo ha hecho el abono individual) para que quede
 * constancia de lo que se había aplicado.
 */
@Entity
@Table(name = "recibo_caja_aplicacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReciboCajaAplicacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recibo_caja_id", nullable = false)
    private Long reciboCajaId;

    @Column(name = "cuenta_cobrar_id", nullable = false)
    private Long cuentaCobrarId;

    @Column(name = "abono_cobrar_id")
    private Long abonoCobrarId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "saldo_anterior", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
