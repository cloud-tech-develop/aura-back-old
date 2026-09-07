package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "asiento_contable")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AsientoContableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 500)
    private String descripcion;

    /** MANUAL | VENTA | COMPRA | GASTO | NOMINA | TESORERIA */
    @Column(name = "tipo_origen", nullable = false, length = 30)
    @Builder.Default
    private String tipoOrigen = "MANUAL";

    @Column(name = "origen_id")
    private Long origenId;

    @Column(name = "total_debito", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalDebito = BigDecimal.ZERO;

    @Column(name = "total_credito", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalCredito = BigDecimal.ZERO;

    /**
     * Consecutivo contable visible por tipo: CD-000001, RC-000045, CE-000120…
     * Prefijos: CD=Diario, VT=Venta, CO=Compra, GS=Gasto, NM=Nómina, TE=Tesorería
     */
    @Column(name = "numero_comprobante", length = 20)
    private String numeroComprobante;

    /** BORRADOR | CONTABILIZADO | ANULADO */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "CONTABILIZADO";

    // ── Cabecera de comprobante manual (CD/CE/RC) ────────────────────────
    /** CD=Diario, CE=Egreso, RC=Ingreso/Recibo de caja. Define el prefijo del consecutivo. */
    @Column(name = "tipo_comprobante", length = 20)
    private String tipoComprobante;

    /** Beneficiario del comprobante (tercero) y snapshot de sus datos para impresión. */
    @Column(name = "beneficiario_tercero_id")
    private Long beneficiarioTerceroId;

    @Column(name = "beneficiario_nombre", length = 200)
    private String beneficiarioNombre;

    @Column(name = "beneficiario_direccion", length = 200)
    private String beneficiarioDireccion;

    @Column(name = "beneficiario_telefono", length = 50)
    private String beneficiarioTelefono;

    /** Ciudad donde se genera el comprobante. */
    @Column(length = 100)
    private String ciudad;

    /** Fecha de vencimiento del comprobante (opcional). */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    // ── Origen de fondos del comprobante (CE/RC) ─────────────────────────
    //
    // Se guarda lo que el comprobante declaró, no lo que se dedujo después: es
    // lo que permite auditar por qué un CE cayó en la caja 3 y no en la 1, y
    // reconstruir el arqueo si alguien pregunta meses más tarde.

    /** Turno de caja afectado; null si la plata no pasó por un cajón. */
    @Column(name = "turno_caja_id")
    private Long turnoCajaId;

    /** EFECTIVO | TRANSFERENCIA | TARJETA… con el que se movió la plata. */
    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    /** Cuenta bancaria de la empresa, cuando el movimiento fue por banco. */
    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    /** El dinero se movió del cajón otro día: no entra a ningún arqueo vivo. */
    @Column(name = "caja_otro_dia", nullable = false)
    @Builder.Default
    private Boolean cajaOtroDia = Boolean.FALSE;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "periodo_contable_id")
    private Long periodoContableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** mappedBy apunta al campo @ManyToOne en AsientoDetalleEntity */
    @OneToMany(mappedBy = "asiento", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<AsientoDetalleEntity> detalles = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
