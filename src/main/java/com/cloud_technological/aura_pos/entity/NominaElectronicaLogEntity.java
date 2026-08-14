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

/**
 * Rastro de cada intento contra la DIAN (V109).
 *
 * <p>Guarda request y response crudos. Cuando la DIAN rechaza un documento, el
 * mensaje suele ser críptico y hay que poder mirar exactamente qué se envió.
 */
@Getter
@Setter
@Entity
@Table(name = "nomina_electronica_log")
public class NominaElectronicaLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nomina_electronica_id", nullable = false)
    private NominaElectronicaEntity nominaElectronica;

    @Column(name = "intento", nullable = false)
    private Integer intento = 1;

    @Column(name = "codigo_respuesta", length = 20)
    private String codigoRespuesta;

    @Column(name = "mensaje_respuesta", columnDefinition = "text")
    private String mensajeRespuesta;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "duracion_ms")
    private Integer duracionMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
