package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "abonos_cobrar")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbonoCobrarEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_cobrar_id")
    private CuentaCobrarEntity cuentaCobrar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_caja_id")
    private TurnoCajaEntity turnoCaja;

    @Column(precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    @Column(length = 255)
    private String referencia;

    /** Cuenta contable donde entró el recaudo, elegida a mano (V142). */
    @Column(name = "cuenta_contable_id")
    private Long cuentaContableId;

    /**
     * La plata se movió del cajón otro día y ese arqueo ya cerró cuadrado
     * (V152). El abono no se ata a ningún turno: solo deja el asiento. Se
     * guarda para poder auditarlo desde el panel de supervisión.
     */
    @Column(name = "caja_otro_dia", nullable = false)
    @Builder.Default
    private Boolean cajaOtroDia = Boolean.FALSE;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    /** Recibo de caja multi-factura al que pertenece (V167); null si fue un abono suelto. */
    @Column(name = "recibo_caja_id")
    private Long reciboCajaId;

    /**
     * Si este abono es una retención que el cliente practicó (medio de pago
     * RETEFUENTE | RETEIVA | RETEICA), el abono en efectivo o banco al que
     * acompaña. Su asiento lo genera ese abono principal; este no lleva uno propio.
     */
    @Column(name = "abono_origen_id")
    private Long abonoOrigenId;

    /** Base sobre la que el cliente calculó la retención (informativa). */
    @Column(name = "base_retencion", precision = 15, scale = 2)
    private java.math.BigDecimal baseRetencion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (fechaPago == null) {
            fechaPago = LocalDateTime.now();
        }
    }

    @PreRemove
    protected void onDelete() {
        deletedAt = LocalDateTime.now();
    }
}
