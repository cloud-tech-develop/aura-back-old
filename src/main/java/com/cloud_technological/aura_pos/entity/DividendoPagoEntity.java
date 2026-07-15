package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Pago de dividendos decretados (E8): DB 2360 · CR caja/banco. Cada pago
 * referencia su distribución; la suma de pagos no supera lo decretado.
 */
@Entity
@Table(name = "dividendo_pago")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DividendoPagoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "distribucion_id", nullable = false)
    private Long distribucionId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo_pago", length = 40)
    private String metodoPago;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "tercero_id")
    private Long terceroId;

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
