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
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Afiliación de un contrato a una entidad de seguridad social (V110).
 *
 * <p>Apunta a {@link TerceroEntity}, <b>no</b> a
 * {@link EntidadSeguridadSocialEntity}: así los pagos, los asientos y exógena
 * funcionan. El código oficial se obtiene navegando
 * {@code tercero.entidadSeguridadSocial.codigoOficial}.
 *
 * <h2>Las fechas no son decorativas</h2>
 * Las banderas de traslado de PILA ({@code tde}, {@code tae}, {@code tdp},
 * {@code tap}) se <b>derivan</b> de detectar un cambio de entidad dentro del
 * período. Sin historial de afiliación, no se pueden calcular.
 */
@Getter
@Setter
@Entity
@Table(name = "contrato_afiliacion")
public class ContratoAfiliacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoLaboralEntity contrato;

    /** La entidad, como tercero. A ella se le gira. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id", nullable = false)
    private TerceroEntity tercero;

    @Column(name = "tipo", length = 10, nullable = false)
    private String tipo;   // EPS | AFP | CCF | ARL

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    /** NULL = vigente. Solo una por (contrato, tipo) puede estarlo. */
    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @Transient
    public boolean estaVigenteEn(LocalDate fecha) {
        if (fecha.isBefore(fechaDesde)) return false;
        return fechaHasta == null || !fecha.isAfter(fechaHasta);
    }

    /**
     * Código oficial para PILA.
     *
     * <p>Devuelve null si el tercero no está enlazado al catálogo nacional —
     * eso es un error de configuración que hace que el archivo salga incompleto.
     * Se valida al guardar la afiliación, no al generar la planilla.
     */
    @Transient
    public String codigoOficial() {
        // V120: la entidad es un tercero con rol; su código UGPP vive en el
        // propio tercero. (Se conserva el fallback al catálogo por si quedaron
        // afiliaciones creadas con el flujo anterior.)
        if (tercero == null) return null;
        if (tercero.getCodigoSeguridadSocial() != null && !tercero.getCodigoSeguridadSocial().isBlank()) {
            return tercero.getCodigoSeguridadSocial();
        }
        return tercero.getEntidadSeguridadSocial() != null
                ? tercero.getEntidadSeguridadSocial().getCodigoOficial()
                : null;
    }
}
