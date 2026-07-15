package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Anticipo (E6): dinero recibido de un cliente sin factura (pasivo 2805) o
 * entregado a un proveedor sin factura (activo 1330). No infla ingresos ni
 * gastos: se cruza contra la factura cuando esta llega.
 */
@Entity
@Table(name = "anticipo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnticipoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** CLIENTE | PROVEEDOR */
    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "tercero_id", nullable = false)
    private Long terceroId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    /** Lo que aún no se ha cruzado contra facturas. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal saldo;

    @Column(name = "metodo_pago", length = 40)
    private String metodoPago;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 300)
    private String observaciones;

    /** ACTIVO | APLICADO | ANULADO */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String estado = "ACTIVO";

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
