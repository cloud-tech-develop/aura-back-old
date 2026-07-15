package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cruce de un anticipo contra una factura (E6): aplica saldo del anticipo
 * a una cuenta por cobrar (cliente) o por pagar (proveedor) sin pasar por
 * caja. Cada cruce genera su asiento (DB 2805·CR 1305 / DB 2205·CR 1330).
 */
@Entity
@Table(name = "anticipo_cruce")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnticipoCruceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "anticipo_id", nullable = false)
    private Long anticipoId;

    @Column(name = "cuenta_cobrar_id")
    private Long cuentaCobrarId;

    @Column(name = "cuenta_pagar_id")
    private Long cuentaPagarId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

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
