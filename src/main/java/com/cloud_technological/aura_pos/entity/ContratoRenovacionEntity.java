package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
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
 * Prórroga de un contrato a término fijo (V102).
 *
 * <p>Deja rastro de cada renovación en vez de sobrescribir
 * {@code contrato.fecha_fin} y perder el historial: hay consecuencias legales
 * atadas al número de prórrogas de un contrato a término fijo.
 */
@Getter
@Setter
@Entity
@Table(name = "contrato_renovacion")
public class ContratoRenovacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoLaboralEntity contrato;

    @Column(name = "fecha_inicial", nullable = false)
    private LocalDate fechaInicial;

    @Column(name = "fecha_final", nullable = false)
    private LocalDate fechaFinal;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
