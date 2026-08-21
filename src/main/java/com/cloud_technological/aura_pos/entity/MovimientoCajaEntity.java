package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movimiento_caja")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoCajaEntity {

    /** Valores de {@link #origenTipo}: qué documento produjo el movimiento. */
    public static final String ORIGEN_COMPRA = "COMPRA";
    public static final String ORIGEN_GASTO = "GASTO";
    public static final String ORIGEN_DEVOLUCION = "DEVOLUCION";
    public static final String ORIGEN_OBLIGACION = "OBLIGACION";
    public static final String ORIGEN_TRASLADO_FONDOS = "TRASLADO_FONDOS";
    /** Movimiento registrado a mano por el cajero, sin documento detrás. */
    public static final String ORIGEN_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_caja_id")
    private TurnoCajaEntity turnoCaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @Column(length = 20)
    private String tipo;

    @Column(length = 255)
    private String concepto;

    @Column(precision = 15, scale = 2)
    private BigDecimal monto;

    /**
     * Cuándo salió o entró la plata. Es la fecha que manda en el arqueo: dice a
     * qué turno pertenece el dinero, independientemente de cuándo se digitó.
     */
    @Column(nullable = false)
    private LocalDate fecha;

    /**
     * Fecha del documento que originó el movimiento, solo cuando difiere de
     * {@link #fecha}. Una factura de compra del día 18 pagada hoy de la caja
     * deja el egreso con fecha de hoy — porque hoy fue cuando salió la plata —
     * y fechaDocumento = 18, para que el cierre lo muestre aparte y el cajero
     * no cuadre a ciegas algo que no reconoce.
     */
    @Column(name = "fecha_documento")
    private LocalDate fechaDocumento;

    /** COMPRA | GASTO | DEVOLUCION | OBLIGACION | TRASLADO_FONDOS | MANUAL. */
    @Column(name = "origen_tipo", length = 30)
    private String origenTipo;

    /** Id del documento dentro de su tipo, para navegar del arqueo al soporte. */
    @Column(name = "origen_id")
    private Long origenId;

    /**
     * true cuando el turno no lo eligió nadie sino que lo dedujo el sistema por
     * ser el único abierto en la sucursal. Sirve para auditar qué movimientos
     * cayeron en una caja por inferencia y no por decisión de una persona.
     */
    @Column(name = "origen_inferido", nullable = false)
    @Builder.Default
    private Boolean origenInferido = Boolean.FALSE;

    /**
     * Corrige un arqueo ya cerrado. Es el único movimiento que puede entrar a un
     * turno CERRADO, y por eso exige motivo y rol autorizador: el cierre
     * original se conserva intacto y este ajuste se muestra encima.
     */
    @Column(name = "es_ajuste_retroactivo", nullable = false)
    @Builder.Default
    private Boolean esAjusteRetroactivo = Boolean.FALSE;

    /** Por qué se corrige. Un ajuste sin motivo es cuadrar la caja a mano. */
    @Column(name = "motivo_ajuste", length = 500)
    private String motivoAjuste;

    @Column(name = "autorizado_por")
    private Integer autorizadoPor;

    /** Concepto de caja elegido (define la cuenta contrapartida del asiento). */
    @Column(name = "concepto_caja_id")
    private Long conceptoCajaId;

    /** EFECTIVO | TRANSFERENCIA — define si la contra-caja va a Caja o a Bancos. */
    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Red de seguridad para los llamadores que aún no informan la fecha:
        // el movimiento sin fecha es el de hoy, que es como se comportaba antes.
        if (fecha == null) {
            fecha = LocalDate.now();
        }
        if (origenInferido == null) {
            origenInferido = Boolean.FALSE;
        }
        if (esAjusteRetroactivo == null) {
            esAjusteRetroactivo = Boolean.FALSE;
        }
    }

    /** El documento es de otro día que el movimiento de caja. */
    public boolean esDeOtraFecha() {
        return fechaDocumento != null && !fechaDocumento.equals(fecha);
    }
}
