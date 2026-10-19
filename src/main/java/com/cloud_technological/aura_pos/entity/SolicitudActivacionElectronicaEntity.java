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
 * Una firma contable pide que se le activen los documentos electrónicos a un cliente
 * gestionado (V228, docs/PLAN_FIRMA_CONTABLE.md §10). La plataforma pone las
 * credenciales de Factus y la solicitud queda atendida, o la rechaza con motivo.
 */
@Entity
@Table(name = "solicitud_activacion_electronica")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudActivacionElectronicaEntity {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ATENDIDA = "ATENDIDA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_empresa_id", nullable = false)
    private Integer clienteEmpresaId;

    @Column(name = "firma_empresa_id", nullable = false)
    private Integer firmaEmpresaId;

    /** FACTURA_VENTA, NOMINA_ELECTRONICA, DOCUMENTO_SOPORTE separados por comas. */
    @Column(nullable = false, length = 120)
    private String servicios;

    @Column(length = 300)
    private String observacion;

    @Column(nullable = false, length = 15)
    private String estado = PENDIENTE;

    @Column(name = "solicitado_por")
    private Integer solicitadoPor;

    @Column(name = "solicitado_at", nullable = false, updatable = false)
    private LocalDateTime solicitadoAt;

    @Column(name = "respondido_por")
    private Integer respondidoPor;

    @Column(name = "respondido_at")
    private LocalDateTime respondidoAt;

    @Column(length = 300)
    private String motivo;

    @PrePersist
    void alCrear() {
        if (solicitadoAt == null) solicitadoAt = LocalDateTime.now();
        if (estado == null) estado = PENDIENTE;
    }
}
