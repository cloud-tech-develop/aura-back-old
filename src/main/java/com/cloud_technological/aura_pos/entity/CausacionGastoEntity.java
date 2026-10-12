package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Causación de un gasto o servicio (V201, docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §4):
 * la factura del proveedor con varias líneas, cada una a su cuenta de gasto, con
 * IVA y retenciones. Deja una cuenta por pagar por el neto y un asiento
 * CAUSACION_GASTO. No es la causación programada (E6).
 */
@Entity
@Table(name = "causacion_gasto")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CausacionGastoEntity {

    public static final String ESTADO_CAUSADA = "CAUSADA";
    public static final String ESTADO_ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "sucursal_id")
    private Integer sucursalId;

    /** Consecutivo interno por empresa: CG-{consecutivo}. */
    @Column(nullable = false)
    private Integer consecutivo;

    @Column(name = "tercero_id", nullable = false)
    private Long terceroId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /** FACTURA | DOCUMENTO_SOPORTE | CUENTA_COBRO | OTRO */
    @Column(name = "tipo_soporte", nullable = false, length = 20)
    private String tipoSoporte;

    @Column(name = "numero_soporte", length = 50)
    private String numeroSoporte;

    @Column(length = 500)
    private String descripcion;

    /** Cuenta por pagar del asiento; sin ella, la del concepto PROVEEDORES. */
    @Column(name = "cuenta_pagar_contable_id")
    private Long cuentaPagarContableId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal iva;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal retefuente;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal reteiva;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal reteica;

    @Column(name = "total_pagar", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPagar;

    @Column(name = "cuenta_pagar_id")
    private Long cuentaPagarId;

    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "anulado_por")
    private Integer anuladoPor;

    @Column(name = "anulado_at")
    private LocalDateTime anuladoAt;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
