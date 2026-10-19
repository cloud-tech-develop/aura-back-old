package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un correo que envía Aura con un documento adjunto (V229,
 * docs/PLAN_CUENTA_COBRO_CORREO.md): factura, cuenta de cobro, cotización o
 * comprobante de nómina. El adjunto se guarda solo mientras el correo espera en la
 * cola (envíos masivos) y se borra al enviarse.
 */
@Entity
@Table(name = "correo_envio")
@Getter
@Setter
@NoArgsConstructor
public class CorreoEnvioEntity {

    public static final String PENDIENTE = "PENDIENTE";
    /** Tomado por la cola; si queda así más de unos minutos, vuelve a PENDIENTE. */
    public static final String ENVIANDO = "ENVIANDO";
    public static final String ENVIADO = "ENVIADO";
    public static final String FALLIDO = "FALLIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "lote_id", length = 36)
    private String loteId;

    /** FACTURA, CUENTA_COBRO, COTIZACION, NOMINA. */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    /** Uno o varios correos separados por coma. */
    @Column(nullable = false, length = 500)
    private String destinatario;

    @Column(length = 500)
    private String cc;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "adjunto_nombre", length = 150)
    private String adjuntoNombre;

    @Column(columnDefinition = "BYTEA")
    private byte[] adjunto;

    @Column(nullable = false, length = 12)
    private String estado = PENDIENTE;

    @Column(nullable = false)
    private Integer intentos = 0;

    /** Id del correo en Resend. */
    @Column(name = "proveedor_id", length = 80)
    private String proveedorId;

    @Column(length = 500)
    private String error;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "enviado_at")
    private LocalDateTime enviadoAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (estado == null) estado = PENDIENTE;
        if (intentos == null) intentos = 0;
    }
}
