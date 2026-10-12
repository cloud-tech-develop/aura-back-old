package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Pago de una declaración (V204): IVA (300), retención (350) o el reteICA de un
 * municipio. Cierra el saldo del período de las cuentas del impuesto contra la
 * caja o el banco, a nombre del acreedor (DIAN o hacienda municipal).
 */
@Entity
@Table(name = "declaracion_pago")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeclaracionPagoEntity {

    public static final String ESTADO_PAGADA = "PAGADA";
    public static final String ESTADO_ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private Integer consecutivo;

    /** IVA | RETENCION | RETEICA */
    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(nullable = false)
    private LocalDate desde;

    @Column(nullable = false)
    private LocalDate hasta;

    @Column(name = "tercero_acreedor_id", nullable = false)
    private Long terceroAcreedorId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "numero_formulario", length = 30)
    private String numeroFormulario;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    /** Redondeo a miles (V207): positivo = se pagó de más. */
    @Column(name = "ajuste_redondeo", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal ajusteRedondeo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal intereses = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal sancion = BigDecimal.ZERO;

    @Column(name = "metodo_pago", nullable = false, length = 20)
    private String metodoPago;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "cuenta_contable_id")
    private Long cuentaContableId;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String estado = ESTADO_PAGADA;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "anulado_por")
    private Integer anuladoPor;

    @Column(name = "anulado_at")
    private LocalDateTime anuladoAt;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;
}
