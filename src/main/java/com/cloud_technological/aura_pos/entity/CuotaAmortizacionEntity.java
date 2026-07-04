package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.*;

/** Una cuota de la tabla de amortización de una obligación financiera. */
@Entity
@Table(name = "cuota_amortizacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CuotaAmortizacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obligacion_id")
    private ObligacionFinancieraEntity obligacion;

    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    /** Cuota total del período (capital + interés). */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cuota;

    @Column(name = "abono_capital", nullable = false, precision = 18, scale = 2)
    private BigDecimal abonoCapital;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal interes;

    /** Saldo de capital después de pagar esta cuota. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal saldo;

    /** PENDIENTE | PAGADA */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    /** Medio de pago con que se canceló la cuota (EFECTIVO, TRANSFERENCIA, ...). */
    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    /**
     * Cuenta bancaria DE DONDE salió el dinero al pagar (origen del pago).
     * Puede diferir de la cuenta del desembolso. Null cuando el pago fue en efectivo.
     */
    @Column(name = "cuenta_bancaria_id_pago")
    private Long cuentaBancariaIdPago;
}
