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
 * Un intento de emitir el documento soporte electrónico de una compra o un
 * gasto a un proveedor no obligado a facturar. Se guardan también los
 * rechazados, con la respuesta de Factus, para ver por qué fallaron.
 */
@Entity
@Table(name = "documento_soporte")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoSoporteEntity {

    public static final String ORIGEN_COMPRA = "COMPRA";
    public static final String ORIGEN_GASTO = "GASTO";

    public static final String ESTADO_ACEPTADO = "ACEPTADO";
    public static final String ESTADO_RECHAZADO = "RECHAZADO";
    public static final String ESTADO_ELIMINADO = "ELIMINADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "origen_tipo", nullable = false, length = 20)
    private String origenTipo;

    @Column(name = "origen_id", nullable = false)
    private Long origenId;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "reference_code", nullable = false, length = 60)
    private String referenceCode;

    @Column(name = "numbering_range_id", length = 20)
    private String numberingRangeId;

    @Column(length = 40)
    private String numero;

    @Column(length = 200)
    private String cude;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(precision = 18, scale = 2)
    private BigDecimal total;

    @Column(precision = 18, scale = 2)
    private BigDecimal retenciones;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
