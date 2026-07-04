package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

/**
 * Obligación financiera (préstamo bancario) con su tabla de amortización.
 * El desembolso genera DB Bancos / CR Obligaciones financieras; cada cuota
 * pagada genera DB Obligaciones (capital) + DB Gasto financiero (interés) / CR Bancos.
 */
@Entity
@Table(name = "obligacion_financiera")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ObligacionFinancieraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** Nombre de la entidad/acreedor (banco). */
    @Column(nullable = false, length = 200)
    private String entidad;

    /** Tercero acreedor (opcional). */
    @Column(name = "tercero_id")
    private Long terceroId;

    /** Número de la obligación / pagaré. */
    @Column(length = 50)
    private String numero;

    @Column(name = "monto_principal", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoPrincipal;

    /** Tasa de interés mensual en porcentaje (ej: 1.5 = 1,5% mensual). */
    @Column(name = "tasa_mensual", nullable = false, precision = 8, scale = 4)
    private BigDecimal tasaMensual;

    @Column(name = "plazo_meses", nullable = false)
    private Integer plazoMeses;

    @Column(name = "fecha_desembolso", nullable = false)
    private LocalDate fechaDesembolso;

    /** Cuenta bancaria donde se recibió el desembolso (opcional). */
    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    /** Saldo de capital pendiente. */
    @Column(name = "saldo_capital", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal saldoCapital = BigDecimal.ZERO;

    /** ACTIVA | PAGADA | ANULADA */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "ACTIVA";

    @OneToMany(mappedBy = "obligacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CuotaAmortizacionEntity> cuotas = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
