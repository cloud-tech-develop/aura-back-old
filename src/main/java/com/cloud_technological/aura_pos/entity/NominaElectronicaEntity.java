package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Documento soporte de pago de nómina electrónica ante la DIAN (V109).
 *
 * <p>Obligatorio. Sin esto el módulo no es vendible como nómina formal.
 *
 * <h2>Idempotencia — lo que más duele si sale mal</h2>
 * El {@link #consecutivo} se <b>reserva antes de enviar</b> y
 * {@code uq_ne_consecutivo} lo protege. Un reintento reusa la misma fila; nunca
 * genera un documento nuevo ante la DIAN. Un consecutivo duplicado ante la DIAN
 * no se deshace con un {@code DELETE}.
 */
@Getter
@Setter
@Entity
@Table(name = "nomina_electronica")
public class NominaElectronicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nomina_id", nullable = false)
    private NominaEntity nomina;

    @Column(name = "agno", nullable = false)
    private Integer agno;

    @Column(name = "mes", nullable = false)
    private Integer mes;

    /** Numeración propia, independiente de la de facturación. */
    @Column(name = "consecutivo", nullable = false)
    private Long consecutivo;

    @Column(name = "prefijo", length = 10)
    private String prefijo;

    @Column(name = "es_ajuste", nullable = false)
    private Boolean esAjuste = Boolean.FALSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nomina_ajustada_id")
    private NominaElectronicaEntity nominaAjustada;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = Estado.PENDIENTE;

    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    /** Identificador único que devuelve la DIAN. Análogo al CUFE de una factura. */
    @Column(name = "cune", length = 120)
    private String cune;

    /**
     * Referencia con la que se registró el documento en Factus (la que se envía
     * en {@code POST /v2/payrolls}). Es la llave para consultarlo o eliminarlo
     * en Factus por {@code /v2/payrolls/reference/{reference_code}}.
     */
    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    /**
     * Snapshot de lo enviado. NO se regenera: es el documento tal cual quedó
     * ante la DIAN. Mismo criterio que {@code pila_cotizante.cod_eps}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

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

    public static final class Estado {
        public static final String PENDIENTE = "PENDIENTE";
        public static final String ENVIADO   = "ENVIADO";
        public static final String ACEPTADO  = "ACEPTADO";
        public static final String RECHAZADO = "RECHAZADO";
        public static final String ANULADO   = "ANULADO";
        private Estado() {}
    }
}
