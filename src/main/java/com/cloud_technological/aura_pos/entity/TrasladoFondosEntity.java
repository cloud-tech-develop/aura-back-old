package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Movimiento de dinero entre dos bolsillos de la propia empresa.
 *
 * <p>No es ni una compra ni un gasto: no hay tercero, no hay resultado, solo
 * cambia dónde está la plata. Hasta que existió este documento, operaciones
 * cotidianas se registraban disfrazadas de otra cosa — constituir la caja menor
 * se colaba como "gasto" y la consignación diaria del efectivo simplemente no
 * se registraba.
 *
 * <p>El caso que lo motivó es la CAJA MENOR. Con ella el administrador de
 * fondos deja de competir por la caja del cajero:
 * <ol>
 *   <li>Constituye: DB 110505 Caja Menor / CR 1105 Caja ó 1110 Bancos</li>
 *   <li>Gasta: DB 5xxx Gasto / CR 110505 — <b>sin tocar el arqueo</b></li>
 *   <li>Reembolsa: DB 110505 / CR 1110 Bancos</li>
 * </ol>
 * El paso 2 no necesita este documento: es un gasto normal eligiendo la caja
 * menor como cuenta de pago.
 */
@Entity
@Table(name = "traslado_fondos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrasladoFondosEntity {

    /** Valores de {@link #origenTipo} y {@link #destinoTipo}. */
    public static final String TIPO_CAJA = "CAJA";
    public static final String TIPO_BANCO = "BANCO";
    public static final String TIPO_CUENTA = "CUENTA";

    /** Valores de {@link #concepto}. */
    public static final String CONCEPTO_CONSTITUCION_CAJA_MENOR = "CONSTITUCION_CAJA_MENOR";
    public static final String CONCEPTO_REEMBOLSO_CAJA_MENOR = "REEMBOLSO_CAJA_MENOR";
    public static final String CONCEPTO_CONSIGNACION = "CONSIGNACION";
    public static final String CONCEPTO_TRASLADO = "TRASLADO";

    public static final String ESTADO_CONFIRMADO = "CONFIRMADO";
    public static final String ESTADO_ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "sucursal_id")
    private Integer sucursalId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    // ── Origen: de dónde sale ───────────────────────────────────────────────
    // Solo se informa el identificador que corresponde al tipo; los otros dos
    // quedan en null. La coherencia la valida el servicio.

    @Column(name = "origen_tipo", nullable = false, length = 20)
    private String origenTipo;

    @Column(name = "origen_turno_caja_id")
    private Long origenTurnoCajaId;

    @Column(name = "origen_cuenta_banco_id")
    private Long origenCuentaBancoId;

    @Column(name = "origen_cuenta_id")
    private Long origenCuentaId;

    // ── Destino: a dónde entra ──────────────────────────────────────────────

    @Column(name = "destino_tipo", nullable = false, length = 20)
    private String destinoTipo;

    @Column(name = "destino_turno_caja_id")
    private Long destinoTurnoCajaId;

    @Column(name = "destino_cuenta_banco_id")
    private Long destinoCuentaBancoId;

    @Column(name = "destino_cuenta_id")
    private Long destinoCuentaId;

    /**
     * Para qué se movió la plata. Es solo una etiqueta — no cambia el asiento —
     * pero permite listar "los reembolsos de caja menor del mes" sin tener que
     * leer las observaciones una por una.
     */
    @Column(nullable = false, length = 40)
    @Builder.Default
    private String concepto = CONCEPTO_TRASLADO;

    @Column(length = 500)
    private String observacion;

    /**
     * Quién responde por el fondo en destino: el administrador de la caja menor.
     * Distinto de {@link #usuarioId}, que es quien digitó el documento.
     */
    @Column(name = "responsable_id")
    private Integer responsableId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = ESTADO_CONFIRMADO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.estado == null) this.estado = ESTADO_CONFIRMADO;
        if (this.concepto == null) this.concepto = CONCEPTO_TRASLADO;
    }

    /** El dinero sale de una caja física, así que mueve su arqueo. */
    public boolean origenEsCaja() {
        return TIPO_CAJA.equals(origenTipo) && origenTurnoCajaId != null;
    }

    /** El dinero entra a una caja física, así que mueve su arqueo. */
    public boolean destinoEsCaja() {
        return TIPO_CAJA.equals(destinoTipo) && destinoTurnoCajaId != null;
    }
}
