package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Nota crédito / débito electrónica (Factus v1). Referencia una factura de venta
 * y la anula/corrige ante la DIAN. Se persiste para guarda anti-reenvío, listar
 * y descargar XML — mismo criterio que {@code nomina_electronica}.
 */
@Getter
@Setter
@Entity
@Table(name = "nota_electronica")
public class NotaElectronicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "tipo", length = 10, nullable = false)
    private String tipo;   // CREDITO | DEBITO

    @Column(name = "reference_code", length = 100, nullable = false)
    private String referenceCode;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "numero", length = 30)
    private String numero;

    @Column(name = "cude", length = 120)
    private String cude;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = Estado.PENDIENTE;

    @Column(name = "customization_id")
    private Integer customizationId;

    @Column(name = "correction_concept_code")
    private Integer correctionConceptCode;

    @Column(name = "total", precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "base_gravable", precision = 15, scale = 2)
    private BigDecimal baseGravable;

    @Column(name = "iva", precision = 15, scale = 2)
    private BigDecimal iva;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "xml", columnDefinition = "text")
    private String xml;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static final class Tipo {
        public static final String CREDITO = "CREDITO";
        public static final String DEBITO  = "DEBITO";
        private Tipo() {}
    }

    public static final class Estado {
        public static final String PENDIENTE = "PENDIENTE";
        public static final String ENVIADO   = "ENVIADO";
        public static final String ACEPTADO  = "ACEPTADO";
        public static final String RECHAZADO = "RECHAZADO";
        public static final String ANULADO   = "ANULADO";
        private Estado() {}
    }
}
