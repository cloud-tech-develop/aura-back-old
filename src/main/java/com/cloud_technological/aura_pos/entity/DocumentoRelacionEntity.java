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
 * Relación entre dos documentos de la cadena documental (V189, fase D0).
 *
 * <p>Una fila dice que una línea (o documento) ORIGEN se aplicó a un documento
 * DESTINO por una cantidad y/o un valor. El pendiente de una línea origen es su
 * cantidad original menos la suma de lo aplicado en estado VIGENTE.
 */
@Entity
@Table(name = "documento_relacion")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoRelacionEntity {

    // Tipos lógicos de documento (se guardan y comparan en mayúsculas).
    public static final String TIPO_COTIZACION = "COTIZACION";
    public static final String TIPO_PEDIDO = "PEDIDO";
    public static final String TIPO_VENTA = "VENTA";
    public static final String TIPO_DEVOLUCION = "DEVOLUCION";
    public static final String TIPO_NOTA_VENTA = "NOTA_VENTA";
    public static final String TIPO_ORDEN_COMPRA = "ORDEN_COMPRA";
    public static final String TIPO_REMISION_COMPRA = "REMISION_COMPRA";
    public static final String TIPO_COMPRA = "COMPRA";
    public static final String TIPO_NOTA_COMPRA = "NOTA_COMPRA";
    public static final String TIPO_RECIBO = "RECIBO";
    public static final String TIPO_EGRESO = "EGRESO";

    public static final String ESTADO_VIGENTE = "VIGENTE";
    public static final String ESTADO_ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "origen_tipo", nullable = false, length = 30)
    private String origenTipo;

    @Column(name = "origen_id", nullable = false)
    private Long origenId;

    @Column(name = "origen_linea_id")
    private Long origenLineaId;

    @Column(name = "destino_tipo", nullable = false, length = 30)
    private String destinoTipo;

    @Column(name = "destino_id", nullable = false)
    private Long destinoId;

    @Column(name = "destino_linea_id")
    private Long destinoLineaId;

    @Column(precision = 18, scale = 6)
    private BigDecimal cantidad;

    @Column(precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 10)
    private String estado;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (estado == null) estado = ESTADO_VIGENTE;
    }
}
