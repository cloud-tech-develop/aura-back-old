package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
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
 * Autorización de un supervisor para pasar el límite de otro usuario (V192):
 * de un solo uso y por pocos minutos. Nace como CODIGO (el supervisor genera un
 * código en su sesión) o SOLICITADA (el cajero pide aprobación remota), pasa a
 * VIGENTE y la venta la deja USADA. Ver {@code AutorizacionService}.
 */
@Entity
@Table(name = "autorizacion")
@Getter
@Setter
@NoArgsConstructor
public class AutorizacionEntity {

    public static final String TIPO_DESCUENTO = "DESCUENTO";
    public static final String CODIGO = "CODIGO";
    public static final String SOLICITADA = "SOLICITADA";
    public static final String VIGENTE = "VIGENTE";
    public static final String USADA = "USADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "solicitante_id")
    private Integer solicitanteId;

    @Column(name = "autorizador_id")
    private Integer autorizadorId;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "descuento_pct", precision = 7, scale = 2)
    private BigDecimal descuentoPct;

    @Column(name = "rebaja_pct", precision = 7, scale = 2)
    private BigDecimal rebajaPct;

    @Column(length = 300)
    private String motivo;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    /** SHA-256 del código; nunca el código. */
    @Column(name = "codigo_hash", length = 64)
    private String codigoHash;

    @Column(nullable = false, length = 12)
    private String estado = VIGENTE;

    @Column(name = "documento_tipo", length = 30)
    private String documentoTipo;

    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "usada_en")
    private LocalDateTime usadaEn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
