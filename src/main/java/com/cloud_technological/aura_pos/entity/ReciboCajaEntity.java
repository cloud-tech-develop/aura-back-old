package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Recibo de caja de cartera (V167): un pago del cliente repartido entre varias
 * cuentas por cobrar. Cada aplicación es un abonos_cobrar normal, así que el
 * arqueo, el asiento RC y los reportes no cambian; el recibo solo agrupa,
 * numera y permite anular todo junto. El sobrante queda como anticipo.
 */
@Entity
@Table(name = "recibo_caja")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReciboCajaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "tercero_id", nullable = false)
    private Long terceroId;

    @Column(nullable = false, length = 30)
    private String numero;

    @Column(nullable = false)
    private Integer consecutivo;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @Column(name = "valor_recibido", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorRecibido;

    /** Lo que se abonó a facturas. */
    @Column(name = "valor_aplicado", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorAplicado;

    /** Sobrante que quedó como anticipo del cliente. */
    @Column(name = "valor_anticipo", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorAnticipo;

    @Column(name = "metodo_pago", nullable = false, length = 30)
    private String metodoPago;

    @Column(length = 255)
    private String referencia;

    @Column(name = "cuenta_contable_id")
    private Long cuentaContableId;

    @Column(name = "turno_caja_id")
    private Long turnoCajaId;

    @Column(name = "caja_otro_dia", nullable = false)
    @Builder.Default
    private Boolean cajaOtroDia = Boolean.FALSE;

    @Column(name = "anticipo_id")
    private Long anticipoId;

    @Column(length = 500)
    private String observaciones;

    /** ACTIVO | ANULADO */
    @Column(nullable = false, length = 12)
    @Builder.Default
    private String estado = "ACTIVO";

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "anulado_por")
    private Integer anuladoPor;

    @Column(name = "anulado_at")
    private LocalDateTime anuladoAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
