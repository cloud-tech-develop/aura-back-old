package com.cloud_technological.aura_pos.entity;

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
import lombok.Getter;
import lombok.Setter;

/** De qué documento entró o salió un serial; guarda cómo estaba para poder anular. */
@Getter
@Setter
@Entity
@Table(name = "documento_serial")
public class DocumentoSerialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30, nullable = false)
    private String origen;

    @Column(name = "detalle_id", nullable = false)
    private Long detalleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serial_id")
    private SerialProductoEntity serial;

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "sucursal_anterior_id")
    private Integer sucursalAnteriorId;

    /** Bodega de la que salió (V172): dos bodegas de la misma sucursal
     *  no se distinguen por sucursalAnteriorId. */
    @Column(name = "bodega_anterior_id")
    private Long bodegaAnteriorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
